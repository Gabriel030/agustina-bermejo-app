package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class CashPaymentAmountInvalidException extends CashPaymentRejectedException {

    public CashPaymentAmountInvalidException(List<PolicyViolation> violations) {
        super("The cash payment amount is not valid", violations);
    }
}
