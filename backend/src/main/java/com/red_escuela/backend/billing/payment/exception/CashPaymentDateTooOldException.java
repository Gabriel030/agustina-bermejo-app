package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class CashPaymentDateTooOldException extends CashPaymentRejectedException {

    public CashPaymentDateTooOldException(List<PolicyViolation> violations) {
        super("The cash payment date is beyond the administrative window", violations);
    }
}
