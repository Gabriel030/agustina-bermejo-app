package com.red_escuela.backend.exception;

public class PaymentReceiptNotFoundException extends RuntimeException {
    public PaymentReceiptNotFoundException(Long id) {
        super("Payment receipt " + id + " not found");
    }
}
