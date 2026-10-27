package com.warehouse.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.ConcurrentUpdateException;
import com.warehouse.common.idempotency.IdempotencyRecord;
import com.warehouse.common.idempotency.IdempotencyRecordRepository;
import com.warehouse.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Runs every stock-changing command with two guarantees:
 *
 * <ol>
 *   <li><b>No lost updates.</b> The command runs in its own transaction. If another transaction
 *       changed the same stock row first (optimistic-lock failure at commit) the whole command is
 *       rolled back and re-run on fresh data, with a short randomised back-off, up to
 *       {@code app.stock.max-attempts} times. Business rules are re-checked on each attempt, so a
 *       retry can still fail with "insufficient stock" if the other writer took it.</li>
 *   <li><b>Exactly-once effect per Idempotency-Key.</b> When the client sends a key, the response is
 *       stored in the same transaction as the change. A repeated request (double click, network
 *       retry) finds the stored response and returns it without touching stock. Two simultaneous
 *       requests with the same key collide on a unique index; the loser rolls back and, on retry,
 *       returns the winner's response.</li>
 * </ol>
 *
 * <p>Callers must not already be inside a transaction: the retry needs a fresh one per attempt.
 */
@Component
public class StockCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(StockCommandExecutor.class);
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_.:-]{8,100}");

    private final TransactionTemplate transactionTemplate;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties.Stock settings;
    private final Clock clock;

    public StockCommandExecutor(PlatformTransactionManager transactionManager,
                                IdempotencyRecordRepository idempotencyRepository, ObjectMapper objectMapper,
                                AppProperties properties, Clock clock) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
        this.settings = properties.stock();
        this.clock = clock;
    }

    /**
     * @param operation      stable name of the command, e.g. {@code "stock.issue"}
     * @param userId         the acting user (keys are scoped per user)
     * @param idempotencyKey value of the {@code Idempotency-Key} header, or {@code null}
     * @param responseType   type of the command's result, used to restore a stored response
     * @param command        the work; must map its result to a DTO before returning (it runs inside the transaction)
     */
    public <T> T execute(String operation, Long userId, String idempotencyKey, Class<T> responseType,
                         Supplier<T> command) {
        String key = normalizeKey(idempotencyKey);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            try {
                return transactionTemplate.execute(status -> runOnce(operation, userId, key, responseType, command));
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
                lastFailure = e;
                log.info("Stock command {} lost a concurrent update race (attempt {}/{})", operation, attempt,
                        settings.maxAttempts());
            } catch (DataIntegrityViolationException e) {
                // A unique/check constraint fired: usually a racing duplicate Idempotency-Key or the
                // concurrent creation of a stock row. A retry resolves both; a genuine violation repeats.
                lastFailure = e;
                log.info("Stock command {} hit a constraint (attempt {}/{}): {}", operation, attempt,
                        settings.maxAttempts(), e.getMostSpecificCause().getMessage());
            }
            backOff(attempt);
        }
        if (lastFailure instanceof DataIntegrityViolationException dive) {
            throw dive;
        }
        throw new ConcurrentUpdateException("Stock command " + operation + " failed after "
                + settings.maxAttempts() + " attempts", lastFailure);
    }

    private <T> T runOnce(String operation, Long userId, String key, Class<T> responseType, Supplier<T> command) {
        if (key != null) {
            Optional<T> replay = idempotencyRepository.findByUserIdAndKey(userId, key)
                    .map(record -> replay(record, operation, responseType));
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        T result = command.get();
        if (key != null) {
            idempotencyRepository.save(new IdempotencyRecord(userId, key, operation, toJson(result), clock.instant()));
        }
        return result;
    }

    private <T> T replay(IdempotencyRecord record, String operation, Class<T> responseType) {
        if (!record.getOperation().equals(operation)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different action");
        }
        try {
            return objectMapper.readValue(record.getResponseJson(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response cannot be read", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response cannot be stored for idempotency", e);
        }
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (!KEY_FORMAT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must be 8-100 characters of letters, digits, '_', '-', '.', ':'");
        }
        return trimmed;
    }

    private void backOff(int attempt) {
        if (attempt >= settings.maxAttempts()) {
            return;
        }
        long base = settings.retryBackoff().toMillis() * attempt;
        try {
            Thread.sleep(base + ThreadLocalRandom.current().nextLong(base + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrentUpdateException("Interrupted while retrying a stock command", e);
        }
    }
}
