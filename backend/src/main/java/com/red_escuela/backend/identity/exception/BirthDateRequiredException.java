package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class BirthDateRequiredException extends BadRequestException {
    public BirthDateRequiredException() {
        super("La fecha de nacimiento es obligatoria");
    }
}
