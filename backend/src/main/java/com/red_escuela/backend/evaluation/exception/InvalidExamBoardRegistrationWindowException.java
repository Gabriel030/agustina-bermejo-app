package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidExamBoardRegistrationWindowException extends BadRequestException {
    public InvalidExamBoardRegistrationWindowException() {
        super("El inicio de la ventana de inscripción no puede ser posterior a su cierre");
    }
}
