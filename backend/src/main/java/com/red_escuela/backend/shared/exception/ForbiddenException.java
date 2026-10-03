package com.red_escuela.backend.shared.exception;

public abstract class ForbiddenException extends RuntimeException {
    protected ForbiddenException() {
        super();
    }

    protected ForbiddenException(String message) {
        super(message);
    }

    protected ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
