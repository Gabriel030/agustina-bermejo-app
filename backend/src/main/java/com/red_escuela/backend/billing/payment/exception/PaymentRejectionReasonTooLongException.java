package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentRejectionReasonTooLongException extends PaymentReviewRejectedException {

    public PaymentRejectionReasonTooLongException(List<PolicyViolation> violations) {
        super("The rejection reason is too long", violations);
    }
}
