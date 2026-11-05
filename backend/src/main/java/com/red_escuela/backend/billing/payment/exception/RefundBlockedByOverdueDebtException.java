package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundBlockedByOverdueDebtException extends RefundConflictException {

    public RefundBlockedByOverdueDebtException(List<PolicyViolation> violations) {
        super("A refund cannot be requested while the student has overdue debt", violations);
    }
}
