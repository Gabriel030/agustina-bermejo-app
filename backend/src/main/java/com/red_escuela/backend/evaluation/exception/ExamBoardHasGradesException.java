package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardHasGradesException extends ConflictException {
    public ExamBoardHasGradesException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " ya tiene notas cargadas");
    }
}
