package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ReceiptMustCoverOldestInvoicesFirstException extends BankTransferReceiptConflictException {

    public ReceiptMustCoverOldestInvoicesFirstException(List<PolicyViolation> violations) {
        super("The receipt must cover the oldest invoices first", violations);
    }
}
