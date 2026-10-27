package com.warehouse.assistant.dto;

import com.warehouse.assistant.query.Column;
import com.warehouse.assistant.query.QueryResult;

import java.util.List;
import java.util.Map;

public record AiSqlAnswer(String sql, boolean answered, String summary, List<Column> columns,
                          List<Map<String, Object>> rows, boolean truncated, String message) {

    public static AiSqlAnswer of(String sql, QueryResult result) {
        return new AiSqlAnswer(sql, true, result.summary(), result.columns(), result.rows(), result.truncated(), null);
    }

    public static AiSqlAnswer failed(String sql, String message) {
        return new AiSqlAnswer(sql, false, null, List.of(), List.of(), false, message);
    }
}
