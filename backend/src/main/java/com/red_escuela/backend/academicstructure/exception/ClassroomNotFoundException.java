package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ClassroomNotFoundException extends NotFoundException {
    public ClassroomNotFoundException(Long id) {
        super("Aula no encontrada: " + id);
    }
}
