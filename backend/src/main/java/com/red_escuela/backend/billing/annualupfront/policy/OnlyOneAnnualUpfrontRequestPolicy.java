package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED"}, decisions = {"D-131"})
public final class OnlyOneAnnualUpfrontRequestPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "OnlyOneAnnualUpfrontRequestPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.requestAlreadyOpen()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED", "Ya hay un pedido de pago anual para este año lectivo.")
                : PolicyVerdict.allow(ruleName());
    }
}
