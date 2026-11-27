package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidExamBoardReopenReasonException extends BadRequestException {
    public InvalidExamBoardReopenReasonException(int minimumLength) {
        super("El motivo de la reapertura del acta debe tener al menos " + minimumLength + " caracteres");
    }
}
