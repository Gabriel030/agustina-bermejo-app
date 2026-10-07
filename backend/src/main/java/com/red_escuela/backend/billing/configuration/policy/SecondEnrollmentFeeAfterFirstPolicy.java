package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "SECOND_ENROLLMENT_FEE_NOT_AFTER_FIRST", decisions = {"D-002"})
public final class SecondEnrollmentFeeAfterFirstPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "SecondEnrollmentFeeAfterFirstPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (change.proposed().enrollmentFeeCount() != 2 || (change.proposed().secondEnrollmentFeeMonth() != null && change.proposed().secondEnrollmentFeeMonth() > change.proposed().firstEnrollmentFeeMonth())) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "SECOND_ENROLLMENT_FEE_NOT_AFTER_FIRST", "Con 2 matrículas, el mes de la segunda debe ser posterior al de la primera.");
    }
}
