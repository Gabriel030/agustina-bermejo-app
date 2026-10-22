package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentRejectionReasonRequiredException extends PaymentReviewRejectedException {

    public PaymentRejectionReasonRequiredException(List<PolicyViolation> violations) {
        super("The rejection reason is required", violations);
    }
}
