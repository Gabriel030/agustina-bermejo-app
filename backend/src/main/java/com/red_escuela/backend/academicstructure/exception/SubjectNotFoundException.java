package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SubjectNotFoundException extends NotFoundException {
    public SubjectNotFoundException(Long id) {
        super("Subject no encontrado: " + id);
    }
}
