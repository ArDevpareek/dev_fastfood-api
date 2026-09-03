package com.dev.fastfood.exception;

// Used when a request is technically valid, but breaks a real rule —
// e.g. ordering from an inactive restaurant, or below the minimum order.
// This will map to a 400 status code.
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}