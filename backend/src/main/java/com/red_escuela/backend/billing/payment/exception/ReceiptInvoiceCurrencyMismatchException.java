package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public class ReceiptInvoiceCurrencyMismatchException extends BankTransferReceiptRejectedException {

    public ReceiptInvoiceCurrencyMismatchException(List<PolicyViolation> violations) {
        super("The selected invoice is not in the currency of the declared amount", violations);
    }
}
