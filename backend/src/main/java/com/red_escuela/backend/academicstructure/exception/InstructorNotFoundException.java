package com.red_escuela.backend.academicstructure.exception;

public class InstructorNotFoundException extends RuntimeException {
    public InstructorNotFoundException(Long id) {
        super("Docente no encontrado: " + id);
    }
}
