package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class HolidayNotFoundException extends NotFoundException {
    public HolidayNotFoundException(Long id) {
        super("Feriado no encontrado: " + id);
    }
}
