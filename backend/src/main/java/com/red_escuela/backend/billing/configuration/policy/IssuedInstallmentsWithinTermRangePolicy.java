package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INSTALLMENTS_OUTSIDE_NEW_TERM_RANGE", decisions = {"D-036"})
public final class IssuedInstallmentsWithinTermRangePolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "IssuedInstallmentsWithinTermRangePolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (!change.hasTermDates() || change.issuedInstallmentPeriods().stream().noneMatch(period -> period.isBefore(change.termStart().get()) || period.isAfter(change.termEnd().get()))) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "INSTALLMENTS_OUTSIDE_NEW_TERM_RANGE", "Hay cuotas ya emitidas fuera de las nuevas fechas del año lectivo.");
    }
}
