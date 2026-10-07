package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "VERSION_ALREADY_EFFECTIVE", decisions = {"D-115"})
public final class ScheduledVersionCancellablePolicy implements Policy<VersionCancellationCheck> {
    public String ruleName() {
        return "ScheduledVersionCancellablePolicy";
    }

    public PolicyVerdict evaluateRule(VersionCancellationCheck cancellation) {
        boolean cancellable = cancellation.status() == ConfigurationVersionStatus.SCHEDULED && cancellation.effectiveFrom().isAfter(cancellation.today());
        return cancellable ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "VERSION_ALREADY_EFFECTIVE", "Solo se puede cancelar una versión programada que todavía no rige.");
    }
}
