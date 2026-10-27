package com.warehouse.assistant.dto;

import com.warehouse.assistant.domain.QueryOutcome;
import com.warehouse.assistant.plan.QueryIntent;
import com.warehouse.assistant.query.Column;

import java.util.List;
import java.util.Map;

/**
 * The assistant's reply.
 *
 * @param interpretation how the question was understood, e.g. "Stockout forecast · next 7 days · your warehouses"
 * @param summary        one sentence computed from the result rows
 * @param message        explanation when the outcome is not ANSWERED
 * @param usedAi         whether the model translated the question (false for quick queries)
 */
public record AssistantAnswer(QueryOutcome outcome, String question, QueryIntent intent, String interpretation,
                              String summary, List<Column> columns, List<Map<String, Object>> rows,
                              boolean truncated, String message, boolean usedAi, long durationMs) {

    public static AssistantAnswer notAnswered(QueryOutcome outcome, String question, String message, boolean usedAi,
                                              long durationMs) {
        return new AssistantAnswer(outcome, question, null, null, null, List.of(), List.of(), false, message, usedAi,
                durationMs);
    }
}
