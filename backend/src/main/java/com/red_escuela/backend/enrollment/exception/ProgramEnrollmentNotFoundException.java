package com.red_escuela.backend.enrollment.exception;

public class ProgramEnrollmentNotFoundException extends RuntimeException {
    public ProgramEnrollmentNotFoundException(Long id) {
        super("Matriculación a carrera no encontrada: " + id);
    }
}
