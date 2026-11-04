package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionCodeAlreadyExistsException extends ConflictException {
    public SectionCodeAlreadyExistsException(String code) {
        super("Ya existe una comisión con el identificador " + code);
    }
}
