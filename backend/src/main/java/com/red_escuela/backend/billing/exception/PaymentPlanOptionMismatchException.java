package com.red_escuela.backend.billing.exception;

public class PaymentPlanOptionMismatchException extends RuntimeException {
    public PaymentPlanOptionMismatchException(Long paymentPlanOptionId) {
        super("Payment plan option " + paymentPlanOptionId + " does not belong to this course/program");
    }
}
