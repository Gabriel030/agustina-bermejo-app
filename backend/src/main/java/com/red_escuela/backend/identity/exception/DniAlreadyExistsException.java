package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class DniAlreadyExistsException extends BadRequestException {
    public DniAlreadyExistsException(String dni) {
        super("Ya existe una persona con DNI: " + dni);
    }
}
