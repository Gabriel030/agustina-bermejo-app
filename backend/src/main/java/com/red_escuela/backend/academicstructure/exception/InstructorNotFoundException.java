package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class InstructorNotFoundException extends NotFoundException {
    public InstructorNotFoundException(Long id) {
        super("Docente no encontrado: " + id);
    }
}
