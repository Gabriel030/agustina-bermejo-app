package com.red_escuela.backend.exception;

public class ProgramSubjectAlreadyExistsException extends RuntimeException {
    public ProgramSubjectAlreadyExistsException(Long programId, Long subjectId) {
        super("La carrera " + programId + " ya incluye el Subject " + subjectId + " en su plan");
    }
}
