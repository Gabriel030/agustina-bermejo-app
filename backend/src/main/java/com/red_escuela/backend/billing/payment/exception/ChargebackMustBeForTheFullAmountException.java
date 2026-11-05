package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ChargebackMustBeForTheFullAmountException extends PaymentReversalConflictException {

    public ChargebackMustBeForTheFullAmountException(List<PolicyViolation> violations) {
        super("A chargeback is always for the full amount of the payment", violations);
    }
}
