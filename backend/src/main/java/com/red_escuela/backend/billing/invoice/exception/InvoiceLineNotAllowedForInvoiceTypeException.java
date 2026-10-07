package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvoiceLineNotAllowedForInvoiceTypeException extends ConflictException {

    public InvoiceLineNotAllowedForInvoiceTypeException() {
        super("This charge is not allowed for this invoice type");
    }
}
