package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class VerifiedPaymentCurrencyMismatchException extends PaymentReviewRejectedException {

    public VerifiedPaymentCurrencyMismatchException(List<PolicyViolation> violations) {
        super("The verified amount currency is not the account currency", violations);
    }
}
