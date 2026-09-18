package com.red_escuela.backend.evaluation.exception;

public class ExamBoardClosedException extends RuntimeException {
    public ExamBoardClosedException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " ya tiene el acta cerrada");
    }
}
