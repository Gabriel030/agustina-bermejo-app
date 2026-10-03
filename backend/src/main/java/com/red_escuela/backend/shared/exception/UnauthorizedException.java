package com.red_escuela.backend.shared.exception;

public abstract class UnauthorizedException extends RuntimeException {
    protected UnauthorizedException() {
        super();
    }

    protected UnauthorizedException(String message) {
        super(message);
    }

    protected UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
