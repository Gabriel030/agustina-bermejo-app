package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidClosureExceptionReasonException extends BadRequestException {
    public InvalidClosureExceptionReasonException(int minimumLength) {
        super("El motivo de la excepción debe tener al menos " + minimumLength + " caracteres");
    }
}
