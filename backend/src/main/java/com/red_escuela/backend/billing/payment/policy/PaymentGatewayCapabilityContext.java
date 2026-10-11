package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RequiredGatewayCapability;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayCapabilities;
import com.red_escuela.backend.shared.domain.Currency;

import java.util.Set;

public record PaymentGatewayCapabilityContext(PaymentGatewayCapabilities capabilities, Currency currency, Set<RequiredGatewayCapability> required) {

    public PaymentGatewayCapabilityContext {
        required = Set.copyOf(required);
    }
}
