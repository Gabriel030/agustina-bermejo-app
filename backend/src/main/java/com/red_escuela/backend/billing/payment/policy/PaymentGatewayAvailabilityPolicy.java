package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PAYMENT_GATEWAY_UNAVAILABLE", decisions = {"D-226"})
public final class PaymentGatewayAvailabilityPolicy implements Policy<PaymentGatewayAvailabilityContext> {

    @Override
    public String ruleName() {
        return "PaymentGatewayAvailabilityPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentGatewayAvailabilityContext context) {
        boolean offlineOrEnabled = !context.requested().isOnlineProvider() || (context.adapterRegistered() && context.enabledByConfiguration());
        return offlineOrEnabled
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PAYMENT_GATEWAY_UNAVAILABLE", "La pasarela de pago online no está habilitada");
    }
}
