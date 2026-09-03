package com.red_escuela.backend.exception;

public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException() {
        super("El token de Google es inválido o expiró");
    }
}
