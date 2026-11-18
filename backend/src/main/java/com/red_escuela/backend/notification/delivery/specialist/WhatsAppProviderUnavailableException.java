package com.red_escuela.backend.notification.delivery.specialist;

public class WhatsAppProviderUnavailableException extends RuntimeException {

    public WhatsAppProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
