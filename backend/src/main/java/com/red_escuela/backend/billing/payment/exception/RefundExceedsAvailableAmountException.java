package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundExceedsAvailableAmountException extends RefundConflictException {

    public RefundExceedsAvailableAmountException(List<PolicyViolation> violations) {
        super("The refund exceeds what the manual payments can still return", violations);
    }
}
