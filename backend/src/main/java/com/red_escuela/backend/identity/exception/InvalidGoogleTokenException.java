package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnauthorizedException;

public class InvalidGoogleTokenException extends UnauthorizedException {
    public InvalidGoogleTokenException() {
        super("El token de Google es inválido o expiró");
    }
}
