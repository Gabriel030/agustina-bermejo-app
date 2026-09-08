package com.red_escuela.backend.billing.exception;

public class InvalidPaymentPlanScopeException extends RuntimeException {
    public InvalidPaymentPlanScopeException(String message) {
        super(message);
    }
}
