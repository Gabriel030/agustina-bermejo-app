package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CANCELLATION_REASON_REQUIRED", decisions = {"D-115"})
public final class PriceCancellationReasonPolicy implements Policy<PriceVersionCancellationCheck> {
    public String ruleName() {
        return "PriceCancellationReasonPolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionCancellationCheck cancellation) {
        String reason = cancellation.reason();
        boolean valid = reason != null && !reason.isBlank() && reason.length() <= PriceChangeReasonPolicy.MAX_REASON_LENGTH;
        return valid ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CANCELLATION_REASON_REQUIRED", "Cancelar una versión necesita un motivo de hasta " + PriceChangeReasonPolicy.MAX_REASON_LENGTH + " caracteres.");
    }
}
