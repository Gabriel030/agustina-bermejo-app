package com.red_escuela.backend.exception;

public class InvoiceAlreadyCancelledException extends RuntimeException {
    public InvoiceAlreadyCancelledException(Long invoiceId) {
        super("La factura " + invoiceId + " ya está CANCELLED");
    }
}
