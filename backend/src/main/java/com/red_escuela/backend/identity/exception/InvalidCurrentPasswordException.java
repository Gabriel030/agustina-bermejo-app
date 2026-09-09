package com.red_escuela.backend.identity.exception;

public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("La contraseña actual no es correcta");
    }
}
