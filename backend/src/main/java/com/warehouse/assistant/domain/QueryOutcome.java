package com.warehouse.assistant.domain;

/** How an assistant question ended; stored in the query log. */
public enum QueryOutcome {
    /** A validated plan ran and returned rows (possibly none). */
    ANSWERED,
    /** The model classified the question as off-topic or as a request to change data. */
    REFUSED,
    /** The model's plan, or a quick-query plan, failed validation (unknown product, bad dates ...). */
    INVALID_PLAN,
    /** AI is disabled, cooling down, or the call failed. */
    AI_UNAVAILABLE,
    /** Unexpected server error while answering. */
    FAILED
}
