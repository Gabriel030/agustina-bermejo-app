package com.red_escuela.backend.shared.exception;

public abstract class ExternalServiceException extends RuntimeException {
    protected ExternalServiceException() {
        super();
    }

    protected ExternalServiceException(String message) {
        super(message);
    }

    protected ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
