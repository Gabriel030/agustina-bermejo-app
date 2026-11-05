package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundFailureReasonInvalidException extends RefundRejectedException {

    public RefundFailureReasonInvalidException(List<PolicyViolation> violations) {
        super("The refund failure reason is not valid", violations);
    }
}
