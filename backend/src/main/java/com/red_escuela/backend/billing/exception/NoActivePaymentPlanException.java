package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class NoActivePaymentPlanException extends ConflictException {
    public NoActivePaymentPlanException(String message) {
        super(message);
    }
}
