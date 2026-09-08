package com.red_escuela.backend.academicstructure.exception;

public class SectionNotFoundException extends RuntimeException {
    public SectionNotFoundException(Long id) {
        super("Comisión no encontrada: " + id);
    }
}
