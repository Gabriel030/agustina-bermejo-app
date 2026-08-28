package com.red_escuela.backend.exception;

import com.red_escuela.backend.entity.PaymentReceiptStatus;

public class InvalidPaymentReceiptStateException extends RuntimeException {
    public InvalidPaymentReceiptStateException(Long id, PaymentReceiptStatus status) {
        super("El comprobante " + id + " no está pendiente de revisión (status actual: " + status + ")");
    }
}
