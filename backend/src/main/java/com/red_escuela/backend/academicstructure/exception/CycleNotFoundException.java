package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CycleNotFoundException extends NotFoundException {
    public CycleNotFoundException(Long id) {
        super("Ciclo no encontrado: " + id);
    }
}
