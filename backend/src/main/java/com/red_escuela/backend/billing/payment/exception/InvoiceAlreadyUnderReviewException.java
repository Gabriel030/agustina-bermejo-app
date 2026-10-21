package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class InvoiceAlreadyUnderReviewException extends BankTransferReceiptConflictException {

    public InvoiceAlreadyUnderReviewException(List<PolicyViolation> violations) {
        super("The selected invoice is already under review by another receipt", violations);
    }
}
