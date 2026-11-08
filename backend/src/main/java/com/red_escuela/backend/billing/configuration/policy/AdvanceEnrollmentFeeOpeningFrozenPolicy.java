package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Objects;

@PolicyRule(violationCodes = "ADVANCE_FEE_OPENING_FROZEN", decisions = {"D-528", "D-201"})
public final class AdvanceEnrollmentFeeOpeningFrozenPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "AdvanceEnrollmentFeeOpeningFrozenPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        boolean unchanged = Objects.equals(change.current().advanceEnrollmentFeeOpensOn(), change.proposed().advanceEnrollmentFeeOpensOn());
        return (!change.advanceEnrollmentFeeIssued() || unchanged) ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_OPENING_FROZEN", "Ya se emitió una matrícula anticipada de este año: no se puede cambiar la fecha de apertura.");
    }
}
