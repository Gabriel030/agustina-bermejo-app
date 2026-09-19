package com.red_escuela.backend.identity.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Token de verificación de email inválido o expirado");
    }
}
