package com.red_escuela.backend.shared.mail;

public class TransactionalEmailRejectedException extends TransactionalEmailDeliveryException {

    private final int statusCode;

    public TransactionalEmailRejectedException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
