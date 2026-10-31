package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_PLAN_CANNOT_BE_CHANGED"}, decisions = {"D-168"})
public final class AnnualUpfrontPlanCannotBeChangedPolicy implements Policy<AnnualUpfrontPlanChange> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontPlanCannotBeChangedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontPlanChange change) {
        return change.annualUpfrontConfirmedForLectiveYear()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_PLAN_CANNOT_BE_CHANGED", "Con el pago anual confirmado el plan no se cambia durante el año lectivo.")
                : PolicyVerdict.allow(ruleName());
    }
}
