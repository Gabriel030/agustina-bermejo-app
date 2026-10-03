package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramNotFoundException extends NotFoundException {
    public ProgramNotFoundException(Long id) {
        super("Program no encontrado: " + id);
    }
}
