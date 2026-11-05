package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class VerifiedPaymentAmountInvalidException extends PaymentReviewRejectedException {

    public VerifiedPaymentAmountInvalidException(List<PolicyViolation> violations) {
        super("The verified payment amount is invalid", violations);
    }
}
