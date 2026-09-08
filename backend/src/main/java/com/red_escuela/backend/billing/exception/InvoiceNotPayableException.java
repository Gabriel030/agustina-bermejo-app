package com.red_escuela.backend.billing.exception;

public class InvoiceNotPayableException extends RuntimeException {
    public InvoiceNotPayableException(Long invoiceId, String status) {
        super("Invoice " + invoiceId + " cannot be paid — status is " + status);
    }
}
