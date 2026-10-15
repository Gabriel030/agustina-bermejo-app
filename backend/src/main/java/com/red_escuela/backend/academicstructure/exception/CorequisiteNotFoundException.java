package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CorequisiteNotFoundException extends NotFoundException {
    public CorequisiteNotFoundException(Long id) {
        super("Co-requisito no encontrado: " + id);
    }
}
