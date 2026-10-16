package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "VERSION_ALREADY_EFFECTIVE", decisions = {"D-115"})
public final class ScheduledPriceVersionCancellablePolicy implements Policy<PriceVersionCancellationCheck> {
    public String ruleName() {
        return "ScheduledPriceVersionCancellablePolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionCancellationCheck cancellation) {
        boolean cancellable = cancellation.status() == PriceVersionStatus.SCHEDULED && cancellation.effectiveFrom().isAfter(cancellation.today());
        return cancellable ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "VERSION_ALREADY_EFFECTIVE", "Solo se puede cancelar una versión programada que todavía no rige.");
    }
}
