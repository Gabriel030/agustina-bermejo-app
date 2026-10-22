package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class LegacyReceiptHasNoVerifiablePaymentException extends PaymentReviewRejectedException {

    public LegacyReceiptHasNoVerifiablePaymentException(List<PolicyViolation> violations) {
        super("A legacy receipt without a payment cannot be approved with verified data", violations);
    }
}
