package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CHANGE_REASON_REQUIRED", decisions = {"D-095"})
public final class PriceChangeReasonPolicy implements Policy<PriceChangeCheck> {
    public static final int MAX_REASON_LENGTH = 1000;

    public String ruleName() {
        return "PriceChangeReasonPolicy";
    }

    public PolicyVerdict evaluateRule(PriceChangeCheck change) {
        String reason = change.changeReason();
        boolean valid = reason != null && !reason.isBlank() && reason.length() <= MAX_REASON_LENGTH;
        return valid ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CHANGE_REASON_REQUIRED", "El cambio de precio necesita un motivo de hasta " + MAX_REASON_LENGTH + " caracteres.");
    }
}
