package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ChargebackReferenceInvalidException extends PaymentReversalRejectedException {

    public ChargebackReferenceInvalidException(List<PolicyViolation> violations) {
        super("The chargeback reference is required and cannot exceed 120 characters", violations);
    }
}
