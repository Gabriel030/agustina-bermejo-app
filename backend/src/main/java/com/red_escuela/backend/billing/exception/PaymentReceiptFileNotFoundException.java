package com.red_escuela.backend.billing.exception;

public class PaymentReceiptFileNotFoundException extends RuntimeException {
    public PaymentReceiptFileNotFoundException(Long receiptId, Throwable cause) {
        super("File of payment receipt " + receiptId + " is not available", cause);
    }
}
