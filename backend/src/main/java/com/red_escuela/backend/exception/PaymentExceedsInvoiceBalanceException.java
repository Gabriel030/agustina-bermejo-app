package com.red_escuela.backend.exception;

public class PaymentExceedsInvoiceBalanceException extends RuntimeException {
    public PaymentExceedsInvoiceBalanceException(Long invoiceId) {
        super("El pago supera el saldo pendiente de la factura " + invoiceId);
    }
}
