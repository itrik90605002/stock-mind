package com.warehouse.forecast.dto;

import java.time.Instant;

/**
 * @param pairsAnalysed  product/warehouse pairs whose usage was analysed
 * @param suggestions    pairs that need reordering
 * @param aiExplanations suggestions whose explanation was written by AI
 */
public record ForecastRunResult(Instant computedAt, int pairsAnalysed, int suggestions, int aiExplanations,
                                long durationMs) {
}
