package com.red_escuela.backend.shared.exception;

public abstract class NotFoundException extends RuntimeException {
    protected NotFoundException() {
        super();
    }

    protected NotFoundException(String message) {
        super(message);
    }

    protected NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
