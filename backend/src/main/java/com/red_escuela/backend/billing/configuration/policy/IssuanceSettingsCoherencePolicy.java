package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.List;

@PolicyRule(violationCodes = "INVALID_ISSUANCE_SETTINGS", decisions = {"D-053", "D-160"})
public final class IssuanceSettingsCoherencePolicy implements Policy<IssuanceSettings> {
    private static final String CODE = "INVALID_ISSUANCE_SETTINGS";

    public String ruleName() {
        return "IssuanceSettingsCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(IssuanceSettings settings) {
        if (settings.maxRetryAttempts() < 0) {
            return deny("Los reintentos no pueden ser negativos.");
        }
        List<Integer> backoff = settings.retryBackoffHours();
        if (backoff.size() != settings.maxRetryAttempts()) {
            return deny("Debe haber una espera por cada reintento.");
        }
        if (backoff.stream().anyMatch(hours -> hours < 1)) {
            return deny("Cada espera debe ser de al menos 1 hora.");
        }
        for (int index = 1; index < backoff.size(); index++) {
            if (backoff.get(index) <= backoff.get(index - 1)) {
                return deny("Las esperas deben ser crecientes.");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }

    private PolicyVerdict deny(String message) {
        return PolicyVerdict.deny(ruleName(), CODE, message);
    }
}
