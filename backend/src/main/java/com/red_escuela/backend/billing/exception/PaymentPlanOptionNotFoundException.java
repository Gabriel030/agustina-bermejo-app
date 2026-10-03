package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PaymentPlanOptionNotFoundException extends NotFoundException {
    public PaymentPlanOptionNotFoundException(Long id) {
        super("Payment plan option not found: " + id);
    }
}
