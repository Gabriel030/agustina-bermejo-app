package com.red_escuela.backend.exception;

public class InvalidPaymentReceiptStateException extends RuntimeException {
    public InvalidPaymentReceiptStateException(Long id, String status) {
        super("Payment receipt " + id + " is not PENDING_REVIEW (current status: " + status + ")");
    }
}
