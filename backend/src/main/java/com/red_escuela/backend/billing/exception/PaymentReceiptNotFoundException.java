package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PaymentReceiptNotFoundException extends NotFoundException {
    public PaymentReceiptNotFoundException(Long id) {
        super("Payment receipt " + id + " not found");
    }
}
