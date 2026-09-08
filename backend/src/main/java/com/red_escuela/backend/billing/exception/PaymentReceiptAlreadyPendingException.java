package com.red_escuela.backend.billing.exception;

public class PaymentReceiptAlreadyPendingException extends RuntimeException {
    public PaymentReceiptAlreadyPendingException(Long invoiceId) {
        super("Invoice " + invoiceId + " already has a payment receipt pending review");
    }
}
