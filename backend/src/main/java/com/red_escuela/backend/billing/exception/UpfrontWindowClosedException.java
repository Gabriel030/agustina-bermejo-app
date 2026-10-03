package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class UpfrontWindowClosedException extends ConflictException {
    public UpfrontWindowClosedException(Long paymentPlanOptionId) {
        super("Upfront window is closed or disabled for payment plan option: " + paymentPlanOptionId);
    }
}
