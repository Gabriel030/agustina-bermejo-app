package com.red_escuela.backend.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(Long id) {
        super("No existe la factura " + id);
    }
}
