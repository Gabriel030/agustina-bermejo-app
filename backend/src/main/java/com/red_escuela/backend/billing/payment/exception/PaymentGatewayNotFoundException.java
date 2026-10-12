package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.shared.exception.NotFoundException;

public class PaymentGatewayNotFoundException extends NotFoundException {

    public PaymentGatewayNotFoundException(PaymentGatewayCode gatewayCode) {
        super("No payment gateway registered for " + gatewayCode);
    }
}
