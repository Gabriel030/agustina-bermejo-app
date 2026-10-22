package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentReviewNoteTooLongException extends PaymentReviewRejectedException {

    public PaymentReviewNoteTooLongException(List<PolicyViolation> violations) {
        super("The review note is too long", violations);
    }
}
