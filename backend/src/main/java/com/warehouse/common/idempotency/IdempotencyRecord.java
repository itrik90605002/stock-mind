package com.warehouse.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The stored response of a stock-changing request, keyed by (user, Idempotency-Key). Saved in
 * the same transaction as the change itself, so a record exists if and only if the change committed.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false)
    private String key;

    /** Which command produced the response; reusing a key for a different command is rejected. */
    @Column(nullable = false)
    private String operation;

    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(Long userId, String key, String operation, String responseJson, Instant createdAt) {
        this.userId = userId;
        this.key = key;
        this.operation = operation;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getOperation() {
        return operation;
    }

    public String getResponseJson() {
        return responseJson;
    }
}
