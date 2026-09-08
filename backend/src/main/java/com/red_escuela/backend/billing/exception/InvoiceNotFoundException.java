package com.red_escuela.backend.billing.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(Long id) {
        super("Invoice not found: " + id);
    }
}
