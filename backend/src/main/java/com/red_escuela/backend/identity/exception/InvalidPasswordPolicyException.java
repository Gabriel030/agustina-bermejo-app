package com.red_escuela.backend.identity.exception;

public class InvalidPasswordPolicyException extends RuntimeException {
    public InvalidPasswordPolicyException(String message) {
        super(message);
    }
}
