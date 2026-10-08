package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NOT_REQUIRED_BEFORE_ENTRY"}, decisions = {"D-020"})
public final class EnrollmentFeeSlotNotBeforeEntryPolicy implements Policy<EnrollmentFeeRequirementContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotNotBeforeEntryPolicy";
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeRequirementContext context) {
        return !EnrollmentFeeSlotNotBeforeEntrySpecification.isSlotBeforeEntry(context.facts(), context.cycle(), context.slot()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_REQUIRED_BEFORE_ENTRY", "la matrícula es anterior al ingreso del alumno");
    }
}
