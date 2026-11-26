package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class TermNotFoundException extends NotFoundException {
    public TermNotFoundException(Long id) {
        super("Term no encontrado: " + id);
    }
}
