package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class TermNotFoundException extends NotFoundException {
    public TermNotFoundException(Long id) {
        super("Term no encontrado: " + id);
    }
}
