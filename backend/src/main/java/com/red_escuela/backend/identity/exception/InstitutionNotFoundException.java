package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class InstitutionNotFoundException extends NotFoundException {
    public InstitutionNotFoundException(Long id) {
        super("Institution no encontrada: " + id);
    }
}
