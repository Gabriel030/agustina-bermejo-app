package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.time.YearMonth;

@PolicyRule(violationCodes = "SECOND_ENROLLMENT_FEE_OUTSIDE_TERM", decisions = {"D-002"})
public final class SecondEnrollmentFeeWithinTermPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "SecondEnrollmentFeeWithinTermPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (!change.hasTermDates() || change.proposed().enrollmentFeeCount() != 2 || change.proposed().secondEnrollmentFeeMonth() == null || withinTerm(change)) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "SECOND_ENROLLMENT_FEE_OUTSIDE_TERM", "La segunda matrícula debe caer dentro del año lectivo.");
    }

    private static boolean withinTerm(YearBillingConfigurationChange change) {
        YearMonth second = EnrollmentFeePeriods.secondPeriod(change.termStart().get(), change.proposed().secondEnrollmentFeeMonth());
        return second.isAfter(change.termStart().get()) && !second.isAfter(change.termEnd().get());
    }
}
