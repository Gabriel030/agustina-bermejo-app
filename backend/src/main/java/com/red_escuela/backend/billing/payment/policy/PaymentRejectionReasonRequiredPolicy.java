package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.PaymentReviewLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_REJECTION_REASON_REQUIRED", "PAYMENT_REJECTION_REASON_TOO_LONG"}, decisions = {"D-139"})
public final class PaymentRejectionReasonRequiredPolicy implements Policy<PaymentRejectionCheck> {

    @Override
    public String ruleName() {
        return "PaymentRejectionReasonRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentRejectionCheck check) {
        if (check.reason() == null || check.reason().isBlank()) {
            return PolicyVerdict.deny(ruleName(), "PAYMENT_REJECTION_REASON_REQUIRED", "El motivo del rechazo es obligatorio.");
        }
        if (check.reason().length() > PaymentReviewLimits.MAX_TEXT_LENGTH) {
            return PolicyVerdict.deny(ruleName(), "PAYMENT_REJECTION_REASON_TOO_LONG", "El motivo del rechazo no puede superar los 500 caracteres.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
