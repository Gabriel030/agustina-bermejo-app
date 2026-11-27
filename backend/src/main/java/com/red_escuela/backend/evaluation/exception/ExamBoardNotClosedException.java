package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardNotClosedException extends ConflictException {
    public ExamBoardNotClosedException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " no tiene el acta cerrada: solo se puede reabrir un acta cerrada");
    }
}
