package com.warehouse.forecast.dto;

import java.time.Instant;
import java.util.List;

public record ReorderSuggestionsResponse(Instant computedAt, List<ReorderSuggestion> suggestions) {
}
