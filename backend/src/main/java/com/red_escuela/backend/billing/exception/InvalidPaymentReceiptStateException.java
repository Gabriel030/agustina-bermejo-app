package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidPaymentReceiptStateException extends ConflictException {
    public InvalidPaymentReceiptStateException(Long id, String status) {
        super("Payment receipt " + id + " is not PENDING_REVIEW (current status: " + status + ")");
    }
}
