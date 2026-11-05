package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ChargebackDateInvalidException extends PaymentReversalRejectedException {

    public ChargebackDateInvalidException(List<PolicyViolation> violations) {
        super("The chargeback date cannot be in the future nor before the payment date", violations);
    }
}
