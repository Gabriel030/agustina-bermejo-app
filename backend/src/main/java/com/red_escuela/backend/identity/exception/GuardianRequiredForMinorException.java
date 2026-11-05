package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class GuardianRequiredForMinorException extends BadRequestException {
    public GuardianRequiredForMinorException() {
        super("Un menor de edad requiere tutor y contacto de emergencia");
    }
}
