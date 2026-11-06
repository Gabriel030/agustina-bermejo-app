package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class AnnualUpfrontReceiptAmountMustBeTotalException extends BankTransferReceiptRejectedException {

    public AnnualUpfrontReceiptAmountMustBeTotalException(List<PolicyViolation> violations) {
        super("The annual upfront receipt must be for the exact total", violations);
    }
}
