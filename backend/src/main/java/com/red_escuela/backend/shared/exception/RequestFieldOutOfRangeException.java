package com.red_escuela.backend.shared.exception;

public class RequestFieldOutOfRangeException extends BadRequestException {
    public RequestFieldOutOfRangeException(String message) {
        super(message);
    }
}
