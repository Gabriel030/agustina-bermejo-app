package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.PaymentReviewLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_REVIEW_NOTE_TOO_LONG"}, decisions = {"D-139"})
public final class PaymentReviewNoteLengthPolicy implements Policy<PaymentVerificationCheck> {

    @Override
    public String ruleName() {
        return "PaymentReviewNoteLengthPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentVerificationCheck check) {
        return check.reviewNote() != null && check.reviewNote().length() > PaymentReviewLimits.MAX_TEXT_LENGTH
                ? PolicyVerdict.deny(ruleName(), "PAYMENT_REVIEW_NOTE_TOO_LONG", "La nota de revisión no puede superar los 500 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
