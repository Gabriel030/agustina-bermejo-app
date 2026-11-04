package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class SectionShiftRequiredException extends BadRequestException {
    public SectionShiftRequiredException() {
        super("El turno es obligatorio para crear una comisión");
    }
}
