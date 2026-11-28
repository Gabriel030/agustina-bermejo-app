package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardWithoutTribunalException extends ConflictException {
    public ExamBoardWithoutTribunalException(Long examBoardId) {
        super("La mesa " + examBoardId + " no tiene tribunal asignado");
    }
}
