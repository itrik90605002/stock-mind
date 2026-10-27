package com.warehouse.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.warehouse.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Calls any OpenAI-compatible {@code POST /chat/completions} endpoint. The same code serves
 * <ul>
 *   <li>local development with Ollama: {@code base-url=http://localhost:11434/v1}, {@code model=llama3.1}</li>
 *   <li>a hosted provider such as Gemini's OpenAI-compatible endpoint: change base URL, API key and model</li>
 * </ul>
 *
 * <p>Requests use temperature 0 and JSON response format. A minimal circuit breaker protects
 * latency: after a failure, calls are skipped for {@code app.ai.cooldown}.
 */
@Component
public class OpenAiCompatibleChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);

    private final AppProperties.Ai settings;
    private final RestClient restClient;
    private final Clock clock;
    private volatile Instant unavailableUntil = Instant.MIN;

    public OpenAiCompatibleChatClient(AppProperties properties, RestClient.Builder builder, Clock clock) {
        this.settings = properties.ai();
        this.clock = clock;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.timeout());
        requestFactory.setReadTimeout(settings.timeout());

        // Buffering sends a Content-Length header instead of a chunked body; some OpenAI-compatible
        // servers and proxies reject chunked requests.
        RestClient.Builder configured = builder.requestFactory(new BufferingClientHttpRequestFactory(requestFactory));
        if (StringUtils.hasText(settings.baseUrl())) {
            configured.baseUrl(settings.baseUrl());
        }
        if (StringUtils.hasText(settings.apiKey())) {
            configured.defaultHeaders(headers -> headers.setBearerAuth(settings.apiKey()));
        }
        this.restClient = configured.build();
    }

    @Override
    public String completeJson(String systemPrompt, String userMessage) {
        if (!settings.enabled()) {
            throw new AiUnavailableException("AI features are disabled");
        }
        if (clock.instant().isBefore(unavailableUntil)) {
            throw new AiUnavailableException("AI service is temporarily unavailable");
        }
        ChatRequest request = new ChatRequest(settings.model(), List.of(
                new Message("system", systemPrompt),
                new Message("user", userMessage)), 0.0, Map.of("type", "json_object"));
        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null
                    || !StringUtils.hasText(response.choices().getFirst().message().content())) {
                throw new AiUnavailableException("AI service returned an empty reply");
            }
            return response.choices().getFirst().message().content();
        } catch (RestClientException | AiUnavailableException e) {
            unavailableUntil = clock.instant().plus(settings.cooldown());
            log.warn("AI call failed ({}); AI features paused for {}", e.getMessage(), settings.cooldown());
            throw e instanceof AiUnavailableException aue ? aue : new AiUnavailableException("AI service call failed", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return settings.enabled() && !clock.instant().isBefore(unavailableUntil);
    }

    @Override
    public boolean isEnabled() {
        return settings.enabled();
    }

    @Override
    public String modelName() {
        return settings.model();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<Message> messages, double temperature,
                       @JsonProperty("response_format") Map<String, String> responseFormat) {
    }

    record Message(String role, String content) {
    }

    /** The subset of the OpenAI chat-completion response we use. */
    record ChatResponse(List<Choice> choices) {
        record Choice(Message message) {
        }
    }
}
