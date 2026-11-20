package com.red_escuela.backend.notification.delivery.specialist;

public class WhatsAppProviderRejectedException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public WhatsAppProviderRejectedException(int statusCode, String errorCode) {
        super("WhatsApp provider rejected the message with status " + statusCode);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
