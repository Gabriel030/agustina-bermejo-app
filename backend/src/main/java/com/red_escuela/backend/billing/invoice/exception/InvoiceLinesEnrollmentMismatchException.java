package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvoiceLinesEnrollmentMismatchException extends ConflictException {

    public InvoiceLinesEnrollmentMismatchException() {
        super("The lines of an invoice must belong to the same enrollment");
    }
}
