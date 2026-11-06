package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "LECTIVE_YEAR_START_FROZEN", decisions = {"D-036", "D-524"})
public final class LectiveYearStartFrozenPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "LectiveYearStartFrozenPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (change.issuedInstallmentPeriods().isEmpty() || !startChanged(change)) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "LECTIVE_YEAR_START_FROZEN", "El período del año lectivo no se puede cambiar una vez emitida la primera cuota.");
    }

    private static boolean startChanged(YearBillingConfigurationChange change) {
        return change.currentTermStart().isPresent() && change.termStart().isPresent() && !change.currentTermStart().equals(change.termStart());
    }
}
