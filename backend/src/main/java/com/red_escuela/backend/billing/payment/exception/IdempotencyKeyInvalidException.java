package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class IdempotencyKeyInvalidException extends CashPaymentRejectedException {

    public IdempotencyKeyInvalidException(List<PolicyViolation> violations) {
        super("The idempotency key is missing or too long", violations);
    }
}
