package com.red_escuela.backend.academicstructure.exception;

public class SectionInstructorNotFoundException extends RuntimeException {
    public SectionInstructorNotFoundException(Long id) {
        super("Asignación de docente no encontrada en esa comisión: " + id);
    }
}
