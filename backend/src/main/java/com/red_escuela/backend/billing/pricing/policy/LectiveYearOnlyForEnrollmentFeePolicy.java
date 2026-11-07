package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "LECTIVE_YEAR_ONLY_FOR_ENROLLMENT_FEE", decisions = {"D-532"})
public final class LectiveYearOnlyForEnrollmentFeePolicy implements Policy<AdvancePriceCheck> {
    public String ruleName() {
        return "LectiveYearOnlyForEnrollmentFeePolicy";
    }

    public PolicyVerdict evaluateRule(AdvancePriceCheck check) {
        return check.feeType() == FeeType.ENROLLMENT_FEE ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "LECTIVE_YEAR_ONLY_FOR_ENROLLMENT_FEE", "El año lectivo de aplicación solo corresponde al precio de la matrícula.");
    }
}
