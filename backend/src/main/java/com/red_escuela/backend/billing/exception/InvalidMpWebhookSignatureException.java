package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.UnauthorizedException;

public class InvalidMpWebhookSignatureException extends UnauthorizedException {
    public InvalidMpWebhookSignatureException(String message) {
        super(message);
    }

    public InvalidMpWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
