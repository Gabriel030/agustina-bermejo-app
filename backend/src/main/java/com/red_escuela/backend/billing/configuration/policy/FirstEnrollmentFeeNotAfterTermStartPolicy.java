package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "FIRST_ENROLLMENT_FEE_AFTER_TERM_START", decisions = {"D-002"})
public final class FirstEnrollmentFeeNotAfterTermStartPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "FirstEnrollmentFeeNotAfterTermStartPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (!change.hasTermDates() || !EnrollmentFeePeriods.firstPeriod(change.termStart().get(), change.proposed().firstEnrollmentFeeMonth()).isAfter(change.termStart().get())) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "FIRST_ENROLLMENT_FEE_AFTER_TERM_START", "La primera matrícula debe ser anterior o igual al inicio del año lectivo.");
    }
}
