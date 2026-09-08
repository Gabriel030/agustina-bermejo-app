package com.red_escuela.backend.academicstructure.exception;

public class PrerequisiteNotFoundException extends RuntimeException {
    public PrerequisiteNotFoundException(Long id) {
        super("Correlatividad no encontrada: " + id);
    }
}
