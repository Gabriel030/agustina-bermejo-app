package com.red_escuela.backend.shared.exception;

public abstract class BadRequestException extends RuntimeException {
    protected BadRequestException() {
        super();
    }

    protected BadRequestException(String message) {
        super(message);
    }

    protected BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
