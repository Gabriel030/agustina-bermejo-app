package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class CashPaymentBackdateReasonInvalidException extends CashPaymentRejectedException {

    public CashPaymentBackdateReasonInvalidException(List<PolicyViolation> violations) {
        super("The backdate reason of the cash payment is too long", violations);
    }
}
