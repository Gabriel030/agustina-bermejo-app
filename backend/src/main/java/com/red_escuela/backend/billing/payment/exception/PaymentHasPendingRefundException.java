package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentHasPendingRefundException extends PaymentReversalConflictException {

    public PaymentHasPendingRefundException(List<PolicyViolation> violations) {
        super("The payment has a pending refund, so it cannot be reversed", violations);
    }
}
