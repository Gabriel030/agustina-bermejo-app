package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PaymentPlanOptionMismatchException extends ConflictException {
    public PaymentPlanOptionMismatchException(Long paymentPlanOptionId) {
        super("Payment plan option " + paymentPlanOptionId + " does not belong to this course/program");
    }
}
