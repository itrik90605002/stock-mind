package com.warehouse.forecast.dto;

import java.time.Instant;
import java.util.List;

/** @param computedAt when the forecasts were last recomputed; null if never */
public record ReorderSuggestionsResponse(Instant computedAt, List<ReorderSuggestion> suggestions) {
}
