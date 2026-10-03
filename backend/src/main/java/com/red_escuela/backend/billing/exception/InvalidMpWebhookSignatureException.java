package com.red_escuela.backend.billing.exception;

public class InvalidMpWebhookSignatureException extends RuntimeException {
    public InvalidMpWebhookSignatureException(String message) {
        super(message);
    }

    public InvalidMpWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
