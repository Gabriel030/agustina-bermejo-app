package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvoiceNotPayableException extends ConflictException {
    public InvoiceNotPayableException(Long invoiceId, String status) {
        super("Invoice " + invoiceId + " cannot be paid — status is " + status);
    }
}
