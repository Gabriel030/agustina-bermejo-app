package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class LegacyReceiptPendingOnInvoiceException extends BankTransferReceiptConflictException {

    public LegacyReceiptPendingOnInvoiceException(List<PolicyViolation> violations) {
        super("A selected invoice already has a pending receipt uploaded through the previous flow", violations);
    }
}
