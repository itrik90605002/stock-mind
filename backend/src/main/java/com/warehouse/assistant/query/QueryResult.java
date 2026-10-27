package com.warehouse.assistant.query;

import java.util.List;
import java.util.Map;

public record QueryResult(List<Column> columns, List<Map<String, Object>> rows, boolean truncated, String summary) {
}
