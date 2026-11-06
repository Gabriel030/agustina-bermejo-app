package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class AnnualUpfrontReceiptMustBeAloneException extends BankTransferReceiptRejectedException {

    public AnnualUpfrontReceiptMustBeAloneException(List<PolicyViolation> violations) {
        super("The annual upfront receipt cannot include other invoices", violations);
    }
}
