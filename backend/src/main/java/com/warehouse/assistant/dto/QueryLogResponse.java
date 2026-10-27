package com.warehouse.assistant.dto;

import com.warehouse.assistant.domain.AiQueryLog;
import com.warehouse.assistant.domain.QueryOutcome;

import java.time.Instant;

public record QueryLogResponse(Long id, Long userId, String userName, String question, String planJson,
                               QueryOutcome outcome, String message, Integer rowCount, int durationMs,
                               Instant createdAt) {

    public static QueryLogResponse from(AiQueryLog log, String userName) {
        return new QueryLogResponse(log.getId(), log.getUserId(), userName, log.getQuestion(), log.getPlanJson(),
                log.getOutcome(), log.getMessage(), log.getRowCount(), log.getDurationMs(), log.getCreatedAt());
    }
}
