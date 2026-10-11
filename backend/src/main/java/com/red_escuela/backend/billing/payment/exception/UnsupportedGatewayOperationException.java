package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;

public class UnsupportedGatewayOperationException extends RuntimeException {

    public UnsupportedGatewayOperationException(PaymentGatewayCode gatewayCode, String operation) {
        super("Gateway " + gatewayCode + " does not support " + operation);
    }
}
