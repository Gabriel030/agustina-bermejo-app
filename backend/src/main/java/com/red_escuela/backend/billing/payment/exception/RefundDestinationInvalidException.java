package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundDestinationInvalidException extends RefundRejectedException {

    public RefundDestinationInvalidException(List<PolicyViolation> violations) {
        super("The refund destination account or holder is too long", violations);
    }
}
