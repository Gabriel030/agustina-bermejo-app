package com.red_escuela.backend.exception;

public class ProgramEnrollmentApplicationNotFoundException extends RuntimeException {
    public ProgramEnrollmentApplicationNotFoundException(Long id) {
        super("Solicitud de alta en carrera no encontrada: " + id);
    }
}
