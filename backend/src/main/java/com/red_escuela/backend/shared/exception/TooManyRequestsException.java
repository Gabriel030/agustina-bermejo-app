package com.red_escuela.backend.shared.exception;

public abstract class TooManyRequestsException extends RuntimeException {
    protected TooManyRequestsException() {
        super();
    }

    protected TooManyRequestsException(String message) {
        super(message);
    }

    protected TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
    }
}
