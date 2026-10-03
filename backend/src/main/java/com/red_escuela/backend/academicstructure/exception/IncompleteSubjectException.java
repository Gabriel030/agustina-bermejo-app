package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class IncompleteSubjectException extends BadRequestException {
    public IncompleteSubjectException(Long id, String reason) {
        super("La materia " + id + " está incompleta: " + reason);
    }
}
