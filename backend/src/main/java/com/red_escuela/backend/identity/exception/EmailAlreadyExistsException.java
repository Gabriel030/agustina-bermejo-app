package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EmailAlreadyExistsException extends BadRequestException {
    public EmailAlreadyExistsException(String email) {
        super("Ya existe una persona con email: " + email);
    }
}
