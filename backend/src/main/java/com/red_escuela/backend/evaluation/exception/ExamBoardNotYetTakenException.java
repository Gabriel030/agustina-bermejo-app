package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardNotYetTakenException extends ConflictException {
    public ExamBoardNotYetTakenException(Long examBoardId) {
        super("La mesa " + examBoardId + " todavía no se tomó: el acta se cierra después del examen");
    }
}
