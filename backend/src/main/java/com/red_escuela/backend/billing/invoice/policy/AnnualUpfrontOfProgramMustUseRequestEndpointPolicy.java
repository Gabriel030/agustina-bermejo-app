package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ANNUAL_UPFRONT_USE_REQUEST_ENDPOINT", decisions = {"D-131", "D-444"})
public final class AnnualUpfrontOfProgramMustUseRequestEndpointPolicy implements Policy<PaymentPlanChoice> {
    public String ruleName() {
        return "AnnualUpfrontOfProgramMustUseRequestEndpointPolicy";
    }

    public PolicyVerdict evaluateRule(PaymentPlanChoice choice) {
        boolean unavailable = choice.annualUpfront() && choice.forProgram();
        return unavailable
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_USE_REQUEST_ENDPOINT", "El pago anual de una carrera se pide desde el pedido del pago anual")
                : PolicyVerdict.allow(ruleName());
    }
}
