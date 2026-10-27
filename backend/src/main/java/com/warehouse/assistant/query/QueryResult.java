package com.warehouse.assistant.query;

import java.util.List;
import java.util.Map;

/**
 * Tabular answer to a validated plan.
 *
 * @param summary   one sentence describing the result, generated from the data (not by AI)
 * @param truncated more rows matched than the plan's limit
 */
public record QueryResult(List<Column> columns, List<Map<String, Object>> rows, boolean truncated, String summary) {
}
