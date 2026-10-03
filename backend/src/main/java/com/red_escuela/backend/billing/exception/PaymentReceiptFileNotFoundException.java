package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PaymentReceiptFileNotFoundException extends NotFoundException {
    public PaymentReceiptFileNotFoundException(Long receiptId, Throwable cause) {
        super("File of payment receipt " + receiptId + " is not available", cause);
    }
}
