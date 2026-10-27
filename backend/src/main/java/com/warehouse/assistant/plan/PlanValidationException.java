package com.warehouse.assistant.plan;

/** A plan cannot be run; the message is safe to show to the user. */
public class PlanValidationException extends RuntimeException {

    public PlanValidationException(String message) {
        super(message);
    }
}
