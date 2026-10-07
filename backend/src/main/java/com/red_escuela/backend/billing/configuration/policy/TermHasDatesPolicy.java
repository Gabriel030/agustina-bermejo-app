package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "TERM_WITHOUT_DATES", decisions = {"D-002"})
public final class TermHasDatesPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "TermHasDatesPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return change.hasTermDates() ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "TERM_WITHOUT_DATES", "El año lectivo no tiene fecha de inicio y de fin: definilas antes de configurar la facturación.");
    }
}
