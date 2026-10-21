package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ReceiptInvoiceNotPayableException extends BankTransferReceiptConflictException {

    public ReceiptInvoiceNotPayableException(List<PolicyViolation> violations) {
        super("The selected invoice cannot be paid with a receipt", violations);
    }
}
