package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.math.BigDecimal;

public class PaymentExceedsInvoiceBalanceException extends ConflictException {
    public PaymentExceedsInvoiceBalanceException(Long invoiceId, BigDecimal amountApplied, BigDecimal remainingBalance) {
        super("Amount applied (" + amountApplied + ") to invoice " + invoiceId + " exceeds its remaining balance (" + remainingBalance + ")");
    }
}
