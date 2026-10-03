package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class InvoiceNotFoundException extends NotFoundException {
    public InvoiceNotFoundException(Long id) {
        super("Invoice not found: " + id);
    }
}
