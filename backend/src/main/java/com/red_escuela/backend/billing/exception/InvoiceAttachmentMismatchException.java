package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvoiceAttachmentMismatchException extends ConflictException {
    public InvoiceAttachmentMismatchException(String detail) {
        super("Invoice cannot be attached to this enrollment: " + detail);
    }
}
