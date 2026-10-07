package com.red_escuela.backend.billing.invoice.exception;

public class InvoiceStatusInconsistentWithAmountsException extends RuntimeException {

    public InvoiceStatusInconsistentWithAmountsException(String detail) {
        super("Invoice status is inconsistent with its amounts: " + detail);
    }
}
