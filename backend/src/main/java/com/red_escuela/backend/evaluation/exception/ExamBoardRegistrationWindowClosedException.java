package com.red_escuela.backend.evaluation.exception;

public class ExamBoardRegistrationWindowClosedException extends RuntimeException {
    public ExamBoardRegistrationWindowClosedException(Long examBoardId) {
        super("La ventana de inscripción de la mesa " + examBoardId + " está cerrada");
    }
}
