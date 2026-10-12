package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardClosedException extends ConflictException {
    public ExamBoardClosedException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " ya tiene el acta cerrada");
    }
}
