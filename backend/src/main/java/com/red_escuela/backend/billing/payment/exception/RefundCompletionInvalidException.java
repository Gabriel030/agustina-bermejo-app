package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundCompletionInvalidException extends RefundRejectedException {

    public RefundCompletionInvalidException(List<PolicyViolation> violations) {
        super("The refund completion date or reference is not valid", violations);
    }
}
