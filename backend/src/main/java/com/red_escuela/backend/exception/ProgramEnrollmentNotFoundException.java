package com.red_escuela.backend.exception;

public class ProgramEnrollmentNotFoundException extends RuntimeException {
    public ProgramEnrollmentNotFoundException(Long id) {
        super("Matriculación a carrera no encontrada: " + id);
    }
}
