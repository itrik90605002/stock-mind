package com.warehouse.ai;

/** The AI service is disabled, cooling down after a failure, or the call failed. Callers degrade gracefully. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
