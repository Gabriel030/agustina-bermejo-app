package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class GraduationRequestNotFoundException extends NotFoundException {
    public GraduationRequestNotFoundException(Long id) {
        super("No existe el trámite de egreso " + id);
    }
}
