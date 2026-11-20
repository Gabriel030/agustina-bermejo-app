package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidGuardianConfirmationTokenException extends BadRequestException {
    public InvalidGuardianConfirmationTokenException() {
        super("El enlace de confirmación no es válido o ya venció");
    }
}
