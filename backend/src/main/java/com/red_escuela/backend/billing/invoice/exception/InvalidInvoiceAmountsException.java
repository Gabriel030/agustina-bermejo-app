package com.red_escuela.backend.billing.invoice.exception;

public class InvalidInvoiceAmountsException extends RuntimeException {

    public InvalidInvoiceAmountsException(String detail) {
        super("Invalid invoice amounts: " + detail);
    }
}
