package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS", decisions = {"D-147"})
public final class GracePeriodWithinSuspensionDaysPolicy implements Policy<LatePaymentSettingsValues> {
    public String ruleName() {
        return "GracePeriodWithinSuspensionDaysPolicy";
    }

    public PolicyVerdict evaluateRule(LatePaymentSettingsValues values) {
        return values.gracePeriodDays() <= values.suspensionAfterDays() ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS", "El período de gracia no puede superar los días para suspender.");
    }
}
