package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ActivationTokenExpiredException extends BadRequestException {
    public ActivationTokenExpiredException() {
        super("El enlace de activación venció");
    }
}
