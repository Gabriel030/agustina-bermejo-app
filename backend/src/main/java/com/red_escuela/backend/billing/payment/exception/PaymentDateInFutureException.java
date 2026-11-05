package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class PaymentDateInFutureException extends CashPaymentRejectedException {

    public PaymentDateInFutureException(List<PolicyViolation> violations) {
        super("The payment date cannot be in the future", violations);
    }
}
