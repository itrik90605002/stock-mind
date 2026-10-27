package com.warehouse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Period;
import java.util.List;

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

    public record Jwt(@NotBlank String secret, @NotNull Duration expiration) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Stock(@Min(1) @Max(10) int maxAttempts, @NotNull Duration retryBackoff,
                        @NotNull Period idempotencyRetention) {
    }

    public record Forecast(@Min(7) int shortWindowDays, @Min(14) int longWindowDays,
                           @Min(0) int reviewPeriodDays, @DecimalMin("0.0") double serviceLevelZ,
                           @Min(0) int defaultLeadTimeDays) {
    }

    public record Ai(boolean enabled, @NotNull Provider provider, String baseUrl, String apiKey, String model,
                     @NotNull Duration timeout, @NotNull Duration cooldown,
                     @Min(1) int questionsPerMinute, @Min(1) @Max(1000) int maxRows) {

        public enum Provider {
            GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
            OPENAI("https://api.openai.com/v1", "gpt-4o-mini");

            private final String baseUrl;
            private final String model;

            Provider(String baseUrl, String model) {
                this.baseUrl = baseUrl;
                this.model = model;
            }
        }

        public boolean active() {
            return enabled && StringUtils.hasText(apiKey);
        }

        public String effectiveBaseUrl() {
            return StringUtils.hasText(baseUrl) ? baseUrl : provider.baseUrl;
        }

        public String effectiveModel() {
            return StringUtils.hasText(model) ? model : provider.model;
        }
    }

    public record Security(@Min(1) int loginAttemptsPerMinute) {
    }

    public record Demo(boolean seedData) {
    }
}
