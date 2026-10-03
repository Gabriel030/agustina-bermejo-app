package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidCurrentPasswordException extends BadRequestException {
    public InvalidCurrentPasswordException() {
        super("La contraseña actual no es correcta");
    }
}
