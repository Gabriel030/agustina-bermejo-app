package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CANCELLATION_REASON_REQUIRED", decisions = {"D-115"})
public final class CancellationReasonRequiredPolicy implements Policy<VersionCancellationCheck> {
    public String ruleName() {
        return "CancellationReasonRequiredPolicy";
    }

    public PolicyVerdict evaluateRule(VersionCancellationCheck cancellation) {
        boolean present = cancellation.reason() != null && !cancellation.reason().isBlank();
        return present ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "CANCELLATION_REASON_REQUIRED", "Cancelar una versión necesita un motivo.");
    }
}
