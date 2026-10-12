package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvoiceAlreadyAttachedException extends ConflictException {
    public InvoiceAlreadyAttachedException(String detail) {
        super("Invoice already attached to an enrollment: " + detail);
    }
}
