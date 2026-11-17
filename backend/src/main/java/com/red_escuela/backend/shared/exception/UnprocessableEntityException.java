package com.red_escuela.backend.shared.exception;

public abstract class UnprocessableEntityException extends RuntimeException {
    protected UnprocessableEntityException() {
        super();
    }

    protected UnprocessableEntityException(String message) {
        super(message);
    }
}
