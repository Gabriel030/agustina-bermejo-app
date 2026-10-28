package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentAlreadyRefundedException extends PaymentReversalConflictException {

    public PaymentAlreadyRefundedException(List<PolicyViolation> violations) {
        super("The money of the payment was already refunded, so it cannot be reversed", violations);
    }
}
