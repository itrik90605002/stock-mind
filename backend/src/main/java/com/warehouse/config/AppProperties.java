package com.warehouse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Period;
import java.util.List;

/**
 * Strongly-typed application settings bound from the {@code app.*} namespace of
 * {@code application.yml}. Every value can be overridden with an environment variable,
 * which is how the app is configured when deployed.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Stock stock,
        @Valid @NotNull Forecast forecast,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Security security,
        @Valid @NotNull Demo demo) {

    /**
     * JWT signing settings.
     *
     * @param secret     HMAC secret; must be at least 32 bytes (256 bits)
     * @param expiration lifetime of an access token
     */
    public record Jwt(@NotBlank String secret, @NotNull Duration expiration) {
    }

    /** Origins (e.g. the deployed frontend URL) allowed to call the API from a browser. */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Stock command execution.
     *
     * @param maxAttempts           attempts for a command that loses an optimistic-lock race
     * @param retryBackoff          base delay between attempts (randomised to avoid lock-step retries)
     * @param idempotencyRetention  how long stored idempotent responses are kept
     */
    public record Stock(@Min(1) @Max(10) int maxAttempts, @NotNull Duration retryBackoff,
                        @NotNull Period idempotencyRetention) {
    }

    /**
     * Reorder-suggestion maths.
     *
     * @param shortWindowDays     recent usage window (weighted more heavily)
     * @param longWindowDays      long usage window, smooths out spikes
     * @param reviewPeriodDays    how often stock is reviewed; an order must cover lead time + this
     * @param serviceLevelZ       z-score for safety stock (1.65 ~ 95% of lead-time demand covered)
     * @param defaultLeadTimeDays used when neither the stock level nor the supplier sets one
     */
    public record Forecast(@Min(7) int shortWindowDays, @Min(14) int longWindowDays,
                           @Min(0) int reviewPeriodDays, @DecimalMin("0.0") double serviceLevelZ,
                           @Min(0) int defaultLeadTimeDays) {
    }

    /**
     * Chat-completion service used by the assistant and reorder explanations. Any OpenAI-compatible
     * {@code /chat/completions} endpoint works: a local Ollama or a hosted provider such as Gemini.
     *
     * @param enabled             master switch; when false AI features report "unavailable"
     * @param baseUrl             base URL of the API (without {@code /chat/completions})
     * @param apiKey              bearer token, blank for local Ollama
     * @param model               chat model name
     * @param timeout             HTTP timeout per call
     * @param cooldown            after a failure, skip AI calls for this long
     * @param questionsPerMinute  per-user rate limit on assistant questions
     * @param maxRows             hard cap on rows returned by an assistant query
     * @param maxExplanations     max reorder suggestions explained by AI per forecast run
     */
    public record Ai(boolean enabled, String baseUrl, String apiKey, String model,
                     @NotNull Duration timeout, @NotNull Duration cooldown,
                     @Min(1) int questionsPerMinute, @Min(1) @Max(1000) int maxRows,
                     @Min(0) int maxExplanations) {
    }

    /** @param loginAttemptsPerMinute per-IP limit on login attempts */
    public record Security(@Min(1) int loginAttemptsPerMinute) {
    }

    /** @param seedData insert demo warehouses, products, users and 90 days of history on first start */
    public record Demo(boolean seedData) {
    }
}
