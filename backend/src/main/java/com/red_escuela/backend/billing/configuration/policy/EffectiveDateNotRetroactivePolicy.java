package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "RETROACTIVE_EFFECTIVE_DATE", decisions = {"D-059", "D-111"})
public final class EffectiveDateNotRetroactivePolicy<C extends EffectiveDated> implements Policy<C> {
    public String ruleName() {
        return "EffectiveDateNotRetroactivePolicy";
    }

    public PolicyVerdict evaluateRule(C values) {
        return !values.effectiveFrom().isBefore(values.today()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "RETROACTIVE_EFFECTIVE_DATE", "La vigencia no puede ser anterior a hoy.");
    }
}
