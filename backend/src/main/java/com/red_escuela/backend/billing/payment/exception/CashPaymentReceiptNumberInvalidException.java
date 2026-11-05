package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class CashPaymentReceiptNumberInvalidException extends CashPaymentRejectedException {

    public CashPaymentReceiptNumberInvalidException(List<PolicyViolation> violations) {
        super("The receipt number is not valid", violations);
    }
}
