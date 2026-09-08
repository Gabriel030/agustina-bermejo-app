package com.red_escuela.backend.billing.exception;

import java.math.BigDecimal;

public class PaymentExceedsInvoiceBalanceException extends RuntimeException {
    public PaymentExceedsInvoiceBalanceException(Long invoiceId, BigDecimal amountApplied, BigDecimal remainingBalance) {
        super("Amount applied (" + amountApplied + ") to invoice " + invoiceId + " exceeds its remaining balance (" + remainingBalance + ")");
    }
}
