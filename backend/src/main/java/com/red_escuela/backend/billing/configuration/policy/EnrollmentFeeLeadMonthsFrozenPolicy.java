package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_LEAD_MONTHS_FROZEN", decisions = {"D-201"})
public final class EnrollmentFeeLeadMonthsFrozenPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "EnrollmentFeeLeadMonthsFrozenPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (change.issuedEnrollmentFeeSlots().isEmpty() || change.current().enrollmentFeeLeadMonths() == change.proposed().enrollmentFeeLeadMonths()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_LEAD_MONTHS_FROZEN", "Ya se emitió una matrícula de este año: no se puede cambiar la anticipación de la matrícula.");
    }
}
