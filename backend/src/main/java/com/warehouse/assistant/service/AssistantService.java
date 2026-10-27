package com.warehouse.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.ai.AiUnavailableException;
import com.warehouse.ai.ChatClient;
import com.warehouse.assistant.domain.AiQueryLog;
import com.warehouse.assistant.domain.AiQueryLogRepository;
import com.warehouse.assistant.domain.QueryOutcome;
import com.warehouse.assistant.dto.AiSqlAnswer;
import com.warehouse.assistant.dto.AssistantAnswer;
import com.warehouse.assistant.dto.AssistantStatus;
import com.warehouse.assistant.dto.QueryLogResponse;
import com.warehouse.assistant.plan.PlanTranslator;
import com.warehouse.assistant.plan.PlanValidationException;
import com.warehouse.assistant.plan.PlanValidator;
import com.warehouse.assistant.plan.QueryIntent;
import com.warehouse.assistant.plan.QueryPlan;
import com.warehouse.assistant.plan.ValidatedPlan;
import com.warehouse.assistant.query.AiSqlExecutor;
import com.warehouse.assistant.query.AssistantQueryExecutor;
import com.warehouse.assistant.query.QueryResult;
import com.warehouse.common.ratelimit.RateLimiter;
import com.warehouse.common.web.PageResponse;
import com.warehouse.config.AppProperties;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.security.AuthUser;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answers plain-English inventory questions two ways from one model call:
 * <ul>
 *   <li>system-built: the model's plan is validated and run as a prewritten query;</li>
 *   <li>AI-built: the model's own SELECT runs in the {@link AiSqlExecutor} sandbox.</li>
 * </ul>
 * The model never sees data. Every question is logged with the model's reply and the outcome.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    public static final String QUICK_QUERY_PREFIX = "Quick query: ";
    private static final String UNAVAILABLE_MESSAGE =
            "The AI assistant is unavailable right now. Use the quick questions below or the regular pages.";

    private final PlanTranslator translator;
    private final PlanValidator validator;
    private final AssistantQueryExecutor executor;
    private final AiSqlExecutor aiSqlExecutor;
    private final ChatClient chatClient;
    private final AiQueryLogRepository logRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final AppProperties.Ai settings;
    private final Clock clock;

    public AssistantService(PlanTranslator translator, PlanValidator validator, AssistantQueryExecutor executor,
                            AiSqlExecutor aiSqlExecutor, ChatClient chatClient, AiQueryLogRepository logRepository,
                            WarehouseRepository warehouseRepository, ProductRepository productRepository,
                            UserRepository userRepository, RateLimiter rateLimiter, ObjectMapper objectMapper,
                            AppProperties properties, Clock clock) {
        this.translator = translator;
        this.validator = validator;
        this.executor = executor;
        this.aiSqlExecutor = aiSqlExecutor;
        this.chatClient = chatClient;
        this.logRepository = logRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.settings = properties.ai();
        this.clock = clock;
    }

    public AssistantStatus status() {
        return new AssistantStatus(chatClient.isEnabled(), chatClient.isAvailable(), chatClient.modelName());
    }

    public AssistantAnswer ask(String rawQuestion, AuthUser user) {
        rateLimiter.check("assistant-ask:" + user.id(), settings.questionsPerMinute(),
                "You are asking questions too quickly. Please wait a minute.");
        String question = sanitize(rawQuestion);
        long started = System.currentTimeMillis();
        if (!chatClient.isAvailable()) {
            return finish(user, question, null, QueryOutcome.AI_UNAVAILABLE, UNAVAILABLE_MESSAGE, null, true, started);
        }
        PlanTranslator.Translation translation;
        try {
            translation = translator.translate(question, context(user));
        } catch (AiUnavailableException e) {
            return finish(user, question, null, QueryOutcome.AI_UNAVAILABLE, UNAVAILABLE_MESSAGE, null, true, started);
        } catch (PlanValidationException e) {
            return finish(user, question, null, QueryOutcome.INVALID_PLAN, e.getMessage(), null, true, started);
        }
        AssistantAnswer answer = execute(user, question, translation.plan(), translation.rawReply(), true, started);
        return answer.outcome() == QueryOutcome.REFUSED ? answer : answer.withAiSql(runAiSql(translation.sql(), user));
    }

    private AiSqlAnswer runAiSql(String sql, AuthUser user) {
        if (sql == null || sql.isBlank()) {
            return AiSqlAnswer.failed(null, "The AI did not write a query for this question.");
        }
        try {
            if (!aiSqlExecutor.isAvailable()) {
                return AiSqlAnswer.failed(sql, "AI-written queries are not set up on this database.");
            }
            List<Long> warehouseIds = warehouseRepository.findAllByOrderByCodeAsc().stream()
                    .filter(w -> user.canAccess(w.getId())).map(w -> w.getId()).toList();
            return AiSqlAnswer.of(sql, aiSqlExecutor.run(sql, warehouseIds, settings.maxRows()));
        } catch (PlanValidationException e) {
            return AiSqlAnswer.failed(sql, e.getMessage());
        } catch (QueryTimeoutException e) {
            return AiSqlAnswer.failed(sql, "The AI's query took too long.");
        } catch (DataAccessException e) {
            log.info("AI-written SQL failed: {}", e.getMostSpecificCause().getMessage());
            return AiSqlAnswer.failed(sql, "The AI's query did not run: " + truncate(e.getMostSpecificCause().getMessage(), 200));
        }
    }

    public AssistantAnswer runPlan(QueryPlan plan, AuthUser user) {
        rateLimiter.check("assistant-run:" + user.id(), settings.questionsPerMinute() * 3,
                "You are running queries too quickly. Please wait a minute.");
        long started = System.currentTimeMillis();
        return execute(user, QUICK_QUERY_PREFIX + (plan.intent() == null ? "" : plan.intent()), plan, toJson(plan),
                false, started);
    }

    private AssistantAnswer execute(AuthUser user, String question, QueryPlan plan, String planJson, boolean usedAi,
                                    long started) {
        if (plan.intent() == null || QueryIntent.UNSUPPORTED.name().equalsIgnoreCase(plan.intent().trim())) {
            String reason = plan.refusalReason() == null || plan.refusalReason().isBlank()
                    ? "I can only answer questions about stock, movements, purchase orders and transfers."
                    : truncate(plan.refusalReason().trim(), 200);
            return finish(user, question, planJson, QueryOutcome.REFUSED, reason, null, usedAi, started);
        }
        ValidatedPlan validated;
        try {
            validated = validator.validate(plan, user);
        } catch (PlanValidationException e) {
            return finish(user, question, planJson, QueryOutcome.INVALID_PLAN, e.getMessage(), null, usedAi, started);
        }
        if (!usedAi) {
            question = QUICK_QUERY_PREFIX + PlanValidator.interpret(validated);
        }
        try {
            QueryResult result = executor.run(validated);
            long duration = System.currentTimeMillis() - started;
            record(user, question, planJson, QueryOutcome.ANSWERED, null, result.rows().size(), duration);
            return new AssistantAnswer(QueryOutcome.ANSWERED, question, validated.intent(),
                    PlanValidator.interpret(validated), result.summary(), result.columns(), result.rows(),
                    result.truncated(), null, usedAi, duration, null);
        } catch (QueryTimeoutException e) {
            return finish(user, question, planJson, QueryOutcome.FAILED,
                    "That query took too long. Try narrowing it to a warehouse, product or shorter period.",
                    null, usedAi, started);
        } catch (RuntimeException e) {
            log.error("Assistant query failed for plan {}", planJson, e);
            return finish(user, question, planJson, QueryOutcome.FAILED,
                    "Something went wrong while answering. Please try again.", null, usedAi, started);
        }
    }

    @Transactional(readOnly = true)
    public List<QueryLogResponse> history(AuthUser user) {
        return logRepository.findTop15ByUserIdOrderByCreatedAtDesc(user.id()).stream()
                .map(l -> QueryLogResponse.from(l, user.fullName())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<QueryLogResponse> allLogs(int page, int size) {
        var logs = logRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        Map<Long, String> names = userRepository.findAllById(logs.map(AiQueryLog::getUserId).toSet()).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.from(logs, l -> QueryLogResponse.from(l, names.getOrDefault(l.getUserId(), "?")));
    }

    private PlanTranslator.Context context(AuthUser user) {
        List<String> warehouses = warehouseRepository.findAllByOrderByCodeAsc().stream()
                .filter(w -> user.canAccess(w.getId()) && w.isActive())
                .map(w -> w.getCode() + ": " + w.getName()).toList();
        return new PlanTranslator.Context(LocalDate.now(clock), warehouses, productRepository.findCategories());
    }

    private AssistantAnswer finish(AuthUser user, String question, String planJson, QueryOutcome outcome,
                                   String message, Integer rows, boolean usedAi, long started) {
        long duration = System.currentTimeMillis() - started;
        record(user, question, planJson, outcome, message, rows, duration);
        return AssistantAnswer.notAnswered(outcome, question, message, usedAi, duration);
    }

    private void record(AuthUser user, String question, String planJson, QueryOutcome outcome, String message,
                        Integer rows, long durationMs) {
        try {
            logRepository.save(new AiQueryLog(user.id(), question, truncate(planJson, 4000), outcome, message, rows,
                    (int) Math.min(durationMs, Integer.MAX_VALUE), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("Could not write assistant query log: {}", e.getMessage());
        }
    }

    private static String sanitize(String question) {
        return question.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private String toJson(QueryPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
