package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IssuedInvoiceCannotBeModifiedException extends ConflictException {

    public IssuedInvoiceCannotBeModifiedException() {
        super("An issued invoice cannot be modified");
    }
}
