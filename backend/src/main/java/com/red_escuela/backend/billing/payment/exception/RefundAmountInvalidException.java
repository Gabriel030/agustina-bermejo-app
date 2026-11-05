package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundAmountInvalidException extends RefundRejectedException {

    public RefundAmountInvalidException(List<PolicyViolation> violations) {
        super("The refund amount is not valid", violations);
    }
}
