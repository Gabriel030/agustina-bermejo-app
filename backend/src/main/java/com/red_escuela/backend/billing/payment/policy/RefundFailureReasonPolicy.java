package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_FAILURE_REASON_INVALID"}, decisions = {"D-159"})
public final class RefundFailureReasonPolicy implements Policy<RefundFailureCheck> {

    @Override
    public String ruleName() {
        return "RefundFailureReasonPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundFailureCheck check) {
        boolean invalid = check.reason() == null || check.reason().isBlank() || check.reason().strip().length() > RefundLimits.MAX_FAILURE_REASON_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "REFUND_FAILURE_REASON_INVALID",
                "El motivo de la falla es obligatorio y tiene hasta " + RefundLimits.MAX_FAILURE_REASON_LENGTH + " caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
