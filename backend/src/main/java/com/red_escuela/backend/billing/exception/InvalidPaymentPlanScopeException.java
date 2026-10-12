package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidPaymentPlanScopeException extends BadRequestException {
    public InvalidPaymentPlanScopeException(String message) {
        super(message);
    }
}
