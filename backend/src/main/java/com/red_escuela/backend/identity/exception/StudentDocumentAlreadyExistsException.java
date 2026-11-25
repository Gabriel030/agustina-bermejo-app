package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentDocumentAlreadyExistsException extends ConflictException {
    public StudentDocumentAlreadyExistsException(DocumentType type, String number) {
        super("Ya existe una persona con documento " + type + ": " + number);
    }
}
