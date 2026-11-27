package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ExamBoardRegistrationEndAfterExamException extends BadRequestException {
    public ExamBoardRegistrationEndAfterExamException() {
        super("La ventana de inscripción no puede cerrar después de la fecha del examen");
    }
}
