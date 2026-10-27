package com.warehouse.common.exception;

/**
 * Thrown when a stock command kept losing optimistic-lock races after every retry. Mapped to
 * HTTP 409 with code {@code CONCURRENT_UPDATE}; the client can safely retry with the same
 * Idempotency-Key.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
