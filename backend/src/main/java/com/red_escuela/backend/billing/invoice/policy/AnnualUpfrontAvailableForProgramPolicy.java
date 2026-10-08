package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ANNUAL_UPFRONT_NOT_AVAILABLE", decisions = {"D-131", "D-191"})
public final class AnnualUpfrontAvailableForProgramPolicy implements Policy<PaymentPlanChoice> {
    public String ruleName() {
        return "AnnualUpfrontAvailableForProgramPolicy";
    }

    public PolicyVerdict evaluateRule(PaymentPlanChoice choice) {
        boolean unavailable = choice.annualUpfront() && choice.forProgram();
        return unavailable
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_NOT_AVAILABLE", "El pago anual de carrera no está disponible")
                : PolicyVerdict.allow(ruleName());
    }
}
