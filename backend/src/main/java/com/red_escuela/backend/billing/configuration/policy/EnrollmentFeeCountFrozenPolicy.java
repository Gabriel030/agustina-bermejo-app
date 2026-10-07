package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_COUNT_FROZEN", decisions = {"D-009", "D-036"})
public final class EnrollmentFeeCountFrozenPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "EnrollmentFeeCountFrozenPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (change.issuedEnrollmentFeeSlots().isEmpty() || change.current().enrollmentFeeCount() == change.proposed().enrollmentFeeCount()) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "ENROLLMENT_FEE_COUNT_FROZEN", "Ya se emitió una matrícula de este año: no se puede cambiar la cantidad de matrículas.");
    }
}
