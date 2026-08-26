package com.red_escuela.backend.exception;

public class SectionEnrollmentNotFoundException extends RuntimeException {
    public SectionEnrollmentNotFoundException(Long id) {
        super("Inscripción a comisión no encontrada: " + id);
    }
}
