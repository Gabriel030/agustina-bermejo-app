package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_ANNUAL_UPFRONT_SETTINGS", decisions = {"D-155", "D-156"})
public final class AnnualUpfrontSettingsCoherencePolicy implements Policy<AnnualUpfrontCheck> {
    private static final String CODE = "INVALID_ANNUAL_UPFRONT_SETTINGS";

    public String ruleName() {
        return "AnnualUpfrontSettingsCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(AnnualUpfrontCheck check) {
        if (check.settings().paymentDeadlineHours() < 1) {
            return PolicyVerdict.deny(ruleName(), CODE, "El plazo de pago debe ser de al menos 1 hora.");
        }
        if (check.settings().requestDeadline().isAfter(check.termStart())) {
            return PolicyVerdict.deny(ruleName(), CODE, "La fecha límite no puede ser posterior a la primera cuota del año lectivo.");
        }
        if (check.settings().requestDeadline().getYear() < check.termStart().getYear() - 1) {
            return PolicyVerdict.deny(ruleName(), CODE, "La fecha límite debe caer dentro del año lectivo o del año anterior.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
