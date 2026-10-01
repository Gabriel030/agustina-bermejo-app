package com.red_escuela.backend.academicstructure.exception;

public class CorequisiteNotFoundException extends RuntimeException {
    public CorequisiteNotFoundException(Long id) {
        super("Co-requisito no encontrado: " + id);
    }
}
