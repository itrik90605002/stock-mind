package com.warehouse.common.exception;

/** Thrown when a caller exceeds a rate limit. Mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
