package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ClassSessionNotFoundException extends NotFoundException {
    public ClassSessionNotFoundException(Long id) {
        super("Clase no encontrada: " + id);
    }
}
