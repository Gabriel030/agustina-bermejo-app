package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CHANGE_REASON_REQUIRED", decisions = {"D-095"})
public final class ConfigurationChangeReasonRequiredPolicy implements Policy<ConfigurationChangeCheck> {
    public String ruleName() {
        return "ConfigurationChangeReasonRequiredPolicy";
    }

    public PolicyVerdict evaluateRule(ConfigurationChangeCheck change) {
        boolean present = change.changeReason() != null && !change.changeReason().isBlank();
        return present ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "CHANGE_REASON_REQUIRED", "El cambio de configuración necesita un motivo.");
    }
}
