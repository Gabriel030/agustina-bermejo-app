package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidGradeCorrectionReasonException extends BadRequestException {
    public InvalidGradeCorrectionReasonException(int minLength, int maxLength) {
        super("El motivo de la corrección debe tener entre " + minLength + " y " + maxLength + " caracteres");
    }
}
