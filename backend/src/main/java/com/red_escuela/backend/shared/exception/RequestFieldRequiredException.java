package com.red_escuela.backend.shared.exception;

public class RequestFieldRequiredException extends BadRequestException {
    public RequestFieldRequiredException(String message) {
        super(message);
    }
}
