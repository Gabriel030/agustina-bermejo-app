package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;

public record PaymentGatewayAvailabilityContext(PaymentGatewayCode requested, boolean adapterRegistered) {
}
