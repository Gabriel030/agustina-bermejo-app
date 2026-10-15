package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramSubjectNotFoundException extends NotFoundException {
    public ProgramSubjectNotFoundException(Long id) {
        super("ProgramSubject no encontrado: " + id);
    }
}
