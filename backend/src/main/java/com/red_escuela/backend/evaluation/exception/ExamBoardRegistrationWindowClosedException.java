package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ExamBoardRegistrationWindowClosedException extends BadRequestException {
    public ExamBoardRegistrationWindowClosedException(Long examBoardId) {
        super("La ventana de inscripción de la mesa " + examBoardId + " está cerrada");
    }
}
