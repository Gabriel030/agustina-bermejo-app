package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class AnnualUpfrontReceiptLateException extends BankTransferReceiptRejectedException {

    public AnnualUpfrontReceiptLateException(List<PolicyViolation> violations) {
        super("The annual upfront receipt was submitted after the payment deadline", violations);
    }
}
