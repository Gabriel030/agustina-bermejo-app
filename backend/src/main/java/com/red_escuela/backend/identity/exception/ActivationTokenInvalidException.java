package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ActivationTokenInvalidException extends BadRequestException {
    public ActivationTokenInvalidException() {
        super("El enlace de activación no es válido");
    }
}
