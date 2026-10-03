package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PaymentReceiptAlreadyPendingException extends ConflictException {
    public PaymentReceiptAlreadyPendingException(Long invoiceId) {
        super("Invoice " + invoiceId + " already has a payment receipt pending review");
    }
}
