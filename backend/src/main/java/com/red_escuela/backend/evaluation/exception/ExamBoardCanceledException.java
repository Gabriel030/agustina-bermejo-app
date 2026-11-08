package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardCanceledException extends ConflictException {
    public ExamBoardCanceledException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " está cancelada: no se puede cerrar el acta");
    }
}
