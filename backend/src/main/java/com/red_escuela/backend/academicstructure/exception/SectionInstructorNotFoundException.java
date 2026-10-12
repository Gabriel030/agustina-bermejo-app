package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SectionInstructorNotFoundException extends NotFoundException {
    public SectionInstructorNotFoundException(Long id) {
        super("Asignación de docente no encontrada en esa comisión: " + id);
    }
}
