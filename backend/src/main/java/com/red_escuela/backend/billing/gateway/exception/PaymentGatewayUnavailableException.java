package com.red_escuela.backend.billing.gateway.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PaymentGatewayUnavailableException extends ConflictException {
    public PaymentGatewayUnavailableException() {
        super("The online payment gateway is not enabled");
    }
}
