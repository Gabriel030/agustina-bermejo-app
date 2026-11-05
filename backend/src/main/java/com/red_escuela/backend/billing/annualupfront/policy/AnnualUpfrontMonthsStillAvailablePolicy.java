package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT"}, decisions = {"D-131"})
public final class AnnualUpfrontMonthsStillAvailablePolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontMonthsStillAvailablePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.monthlyInstallmentAlreadyIssued()
                ? PolicyVerdict.deny(ruleName(), "NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT", "Alguna cuota del año ya fue emitida: no se puede pagar el año completo.")
                : PolicyVerdict.allow(ruleName());
    }
}
