package com.warehouse.forecast.dto;

import java.time.Instant;

public record ForecastRunResult(Instant computedAt, int pairsAnalysed, int suggestions, long durationMs) {
}
