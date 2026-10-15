package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Objects;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_MONTH_FROZEN", decisions = {"D-036"})
public final class EnrollmentFeeMonthFrozenPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "EnrollmentFeeMonthFrozenPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return change.issuedEnrollmentFeeSlots().stream().allMatch(slot -> slot > change.proposed().enrollmentFeeCount() || Objects.equals(change.current().enrollmentFeeMonthOfSlot(slot), change.proposed().enrollmentFeeMonthOfSlot(slot))) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_MONTH_FROZEN", "Ya se emitió una matrícula de este año: no se puede cambiar su mes.");
    }
}
