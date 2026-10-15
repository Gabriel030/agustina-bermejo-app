package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ExternalServiceException;

public class MercadoPagoGatewayException extends ExternalServiceException {

    public MercadoPagoGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
