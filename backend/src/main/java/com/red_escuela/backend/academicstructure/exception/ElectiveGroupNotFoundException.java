package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ElectiveGroupNotFoundException extends NotFoundException {
    public ElectiveGroupNotFoundException(Long id) {
        super("ElectiveGroup no encontrado: " + id);
    }
}
