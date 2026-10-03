package com.red_escuela.backend.evaluation.exception;

public class ExamBoardNotYetTakenException extends RuntimeException {
    public ExamBoardNotYetTakenException(Long examBoardId) {
        super("La mesa " + examBoardId + " todavía no se tomó: el acta se cierra después del examen");
    }
}
