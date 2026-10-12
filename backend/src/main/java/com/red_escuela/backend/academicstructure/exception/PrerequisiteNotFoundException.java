package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PrerequisiteNotFoundException extends NotFoundException {
    public PrerequisiteNotFoundException(Long id) {
        super("Correlatividad no encontrada: " + id);
    }
}
