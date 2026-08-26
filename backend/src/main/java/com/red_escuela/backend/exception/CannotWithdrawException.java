package com.red_escuela.backend.exception;

public class CannotWithdrawException extends RuntimeException {
    public CannotWithdrawException(String message) {
        super(message);
    }
}
