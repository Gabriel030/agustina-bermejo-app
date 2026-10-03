package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SectionNotFoundException extends NotFoundException {
    public SectionNotFoundException(Long id) {
        super("Comisión no encontrada: " + id);
    }
}
