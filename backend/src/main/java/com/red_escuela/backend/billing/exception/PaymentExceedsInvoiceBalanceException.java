package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import com.red_escuela.backend.shared.domain.Money;

public class PaymentExceedsInvoiceBalanceException extends ConflictException {
    public PaymentExceedsInvoiceBalanceException(Long invoiceId, Money amountApplied, Money remainingBalance) {
        super("Amount applied (" + amountApplied.amount() + " " + amountApplied.currency() + ") to invoice " + invoiceId + " exceeds its remaining balance (" + remainingBalance.amount() + " " + remainingBalance.currency() + ")");
    }
}
