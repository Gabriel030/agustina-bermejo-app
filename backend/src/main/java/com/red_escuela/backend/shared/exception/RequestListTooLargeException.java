package com.red_escuela.backend.shared.exception;

public class RequestListTooLargeException extends BadRequestException {
    public RequestListTooLargeException(String message) {
        super(message);
    }
}
