package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class DniAlreadyExistsException extends BadRequestException {
    public DniAlreadyExistsException(DocumentType type, String number) {
        super("Ya existe una persona con documento " + type + ": " + number);
    }
}
