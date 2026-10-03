package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class GradeItemNotFoundException extends NotFoundException {
    public GradeItemNotFoundException(Long id) {
        super("Nota no encontrada: " + id);
    }
}
