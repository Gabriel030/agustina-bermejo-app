package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class CashPaymentBackdateReasonRequiredException extends CashPaymentRejectedException {

    public CashPaymentBackdateReasonRequiredException(List<PolicyViolation> violations) {
        super("A backdated cash payment needs a reason", violations);
    }
}
