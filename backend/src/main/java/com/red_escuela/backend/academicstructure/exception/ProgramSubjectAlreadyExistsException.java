package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramSubjectAlreadyExistsException extends ConflictException {
    public ProgramSubjectAlreadyExistsException(Long programId, Long subjectId) {
        super("La carrera " + programId + " ya incluye el Subject " + subjectId + " en su plan");
    }
}
