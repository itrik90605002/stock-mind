package com.warehouse.assistant.dto;

import com.warehouse.assistant.domain.QueryOutcome;
import com.warehouse.assistant.plan.QueryIntent;
import com.warehouse.assistant.query.Column;

import java.util.List;
import java.util.Map;

public record AssistantAnswer(QueryOutcome outcome, String question, QueryIntent intent, String interpretation,
                              String summary, List<Column> columns, List<Map<String, Object>> rows,
                              boolean truncated, String message, boolean usedAi, long durationMs,
                              AiSqlAnswer aiSql) {

    public AssistantAnswer withAiSql(AiSqlAnswer answer) {
        return new AssistantAnswer(outcome, question, intent, interpretation, summary, columns, rows, truncated, message,
                usedAi, durationMs, answer);
    }

    public static AssistantAnswer notAnswered(QueryOutcome outcome, String question, String message, boolean usedAi,
                                              long durationMs) {
        return new AssistantAnswer(outcome, question, null, null, null, List.of(), List.of(), false, message, usedAi,
                durationMs, null);
    }
}
