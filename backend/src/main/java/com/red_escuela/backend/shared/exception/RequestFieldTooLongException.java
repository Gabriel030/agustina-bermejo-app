package com.red_escuela.backend.shared.exception;

public class RequestFieldTooLongException extends BadRequestException {
    public RequestFieldTooLongException(String message) {
        super(message);
    }
}
