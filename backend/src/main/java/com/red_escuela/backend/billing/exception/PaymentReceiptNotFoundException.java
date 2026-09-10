package com.red_escuela.backend.billing.exception;

public class PaymentReceiptNotFoundException extends RuntimeException {
    public PaymentReceiptNotFoundException(Long id) {
        super("Payment receipt " + id + " not found");
    }
}
