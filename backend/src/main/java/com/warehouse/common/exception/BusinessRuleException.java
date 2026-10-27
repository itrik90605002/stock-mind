package com.warehouse.common.exception;

/**
 * Thrown when a request is well-formed but violates an inventory rule (e.g. not enough available
 * stock, transfer already dispatched). Mapped to HTTP 409 with a machine-readable {@code code} the
 * frontend can switch on.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
