package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "NEGATIVE_SUSPENSION_DAYS", decisions = {"D-147"})
public final class SuspensionDaysNotNegativePolicy implements Policy<LatePaymentSettingsValues> {
    public String name() {
        return "SuspensionDaysNotNegativePolicy";
    }

    public PolicyVerdict evaluate(LatePaymentSettingsValues values) {
        return values.suspensionAfterDays() >= 0 ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "NEGATIVE_SUSPENSION_DAYS", "Los días para suspender no pueden ser negativos.");
    }
}
