package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundMethodMismatchException extends RefundConflictException {

    public RefundMethodMismatchException(List<PolicyViolation> violations) {
        super("A refund part must be completed by the same method as its original payment", violations);
    }
}
