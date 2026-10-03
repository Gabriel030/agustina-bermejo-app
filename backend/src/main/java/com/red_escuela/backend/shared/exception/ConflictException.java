package com.red_escuela.backend.shared.exception;

public abstract class ConflictException extends RuntimeException {
    protected ConflictException() {
        super();
    }

    protected ConflictException(String message) {
        super(message);
    }

    protected ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
