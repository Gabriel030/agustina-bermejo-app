package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class RefundDestinationRequiredException extends RefundRejectedException {

    public RefundDestinationRequiredException(List<PolicyViolation> violations) {
        super("Every refund part needs a destination account and holder", violations);
    }
}
