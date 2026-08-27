package com.red_escuela.backend.exception;

public class InvalidPasswordPolicyException extends RuntimeException {
    public InvalidPasswordPolicyException(String message) {
        super(message);
    }
}
