package com.red_escuela.backend.exception;

public class PaymentReceiptAlreadyPendingException extends RuntimeException {
    public PaymentReceiptAlreadyPendingException(Long invoiceId) {
        super("La factura " + invoiceId + " ya tiene un comprobante pendiente de revisión");
    }
}
