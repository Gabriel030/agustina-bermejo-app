package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ActiveTermAlreadyExistsException extends ConflictException {
    public ActiveTermAlreadyExistsException(Integer year, TermType type) {
        super("Ya existe un período activo del año " + year + " y tipo " + type);
    }
}
