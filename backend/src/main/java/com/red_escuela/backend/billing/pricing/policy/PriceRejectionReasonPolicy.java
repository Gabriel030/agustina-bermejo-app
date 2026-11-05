package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "REJECTION_REASON_REQUIRED", decisions = {"D-114"})
public final class PriceRejectionReasonPolicy implements Policy<PriceRejectionCheck> {
    public String ruleName() {
        return "PriceRejectionReasonPolicy";
    }

    public PolicyVerdict evaluateRule(PriceRejectionCheck rejection) {
        boolean valid = rejection.reason() != null && !rejection.reason().isBlank();
        return valid ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "REJECTION_REASON_REQUIRED", "Rechazar una propuesta de precio necesita un motivo.");
    }
}
