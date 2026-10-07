package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "RETROACTIVE_EFFECTIVE_DATE", decisions = {"D-059"})
public final class EffectiveDateNotRetroactivePolicy implements Policy<LatePaymentSettingsValues> {
    public String ruleName() {
        return "EffectiveDateNotRetroactivePolicy";
    }

    public PolicyVerdict evaluateRule(LatePaymentSettingsValues values) {
        return !values.effectiveFrom().isBefore(values.today()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "RETROACTIVE_EFFECTIVE_DATE", "La vigencia de la política de mora no puede ser anterior a hoy.");
    }
}
