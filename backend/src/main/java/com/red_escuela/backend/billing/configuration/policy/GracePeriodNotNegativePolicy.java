package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "NEGATIVE_GRACE_PERIOD", decisions = {"D-147"})
public final class GracePeriodNotNegativePolicy implements Policy<LatePaymentSettingsValues> {
    public String name() {
        return "GracePeriodNotNegativePolicy";
    }

    public PolicyVerdict evaluate(LatePaymentSettingsValues values) {
        return values.gracePeriodDays() >= 0 ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "NEGATIVE_GRACE_PERIOD", "El período de gracia no puede ser negativo.");
    }
}
