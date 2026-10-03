package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidElectiveRangeException extends BadRequestException {
    public InvalidElectiveRangeException(Integer minRequired, Integer maxAllowed) {
        super("Rango de grupo electivo inválido: minRequired=" + minRequired + " > maxAllowed=" + maxAllowed);
    }
}
