package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class FinalGradeRequiresExamBoardException extends BadRequestException {
    public FinalGradeRequiresExamBoardException() {
        super("La nota del examen final se carga solo desde la mesa de examen");
    }
}
