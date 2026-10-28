package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED"}, decisions = {"D-156"})
public final class AnnualUpfrontRequestWindowPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontRequestWindowPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.requestDeadline() == null || check.today().isAfter(check.requestDeadline())
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED", "Ya pasó la fecha límite para pagar el año completo.")
                : PolicyVerdict.allow(ruleName());
    }
}
