package com.red_escuela.backend.billing.exception;

public class MercadoPagoGatewayException extends RuntimeException {

    public MercadoPagoGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
