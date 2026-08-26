package com.red_escuela.backend.exception;

public class ProgramSubjectNotFoundException extends RuntimeException {
    public ProgramSubjectNotFoundException(Long id) {
        super("ProgramSubject no encontrado: " + id);
    }
}
