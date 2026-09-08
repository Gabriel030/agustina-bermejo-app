package com.red_escuela.backend.billing.exception;

public class PaymentPlanOptionNotFoundException extends RuntimeException {
    public PaymentPlanOptionNotFoundException(Long id) {
        super("Payment plan option not found: " + id);
    }
}
