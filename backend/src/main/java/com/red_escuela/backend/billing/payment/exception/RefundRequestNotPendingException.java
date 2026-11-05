package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RefundRequestNotPendingException extends ConflictException {

    public RefundRequestNotPendingException() {
        super("The refund request has no pending parts to approve");
    }
}
