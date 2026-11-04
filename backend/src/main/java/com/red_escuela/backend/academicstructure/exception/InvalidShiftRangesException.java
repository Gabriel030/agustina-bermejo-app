package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidShiftRangesException extends BadRequestException {
    public InvalidShiftRangesException(String reason) {
        super("Los rangos horarios de los turnos no son coherentes: " + reason);
    }
}
