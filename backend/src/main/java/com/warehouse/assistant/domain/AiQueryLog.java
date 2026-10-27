package com.warehouse.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_query_logs")
public class AiQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String question;

    @Column(name = "plan_json")
    private String planJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueryOutcome outcome;

    private String message;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiQueryLog() {
    }

    public AiQueryLog(Long userId, String question, String planJson, QueryOutcome outcome, String message,
                      Integer rowCount, int durationMs, Instant createdAt) {
        this.userId = userId;
        this.question = truncate(question, 500);
        this.planJson = planJson;
        this.outcome = outcome;
        this.message = truncate(message, 500);
        this.rowCount = rowCount;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public String getPlanJson() {
        return planJson;
    }

    public QueryOutcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
