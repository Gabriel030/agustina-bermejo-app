package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidClosureReopenReasonException extends BadRequestException {
    public InvalidClosureReopenReasonException(int minimumLength) {
        super("El motivo de la reapertura debe tener al menos " + minimumLength + " caracteres");
    }
}
