package com.red_escuela.backend.evaluation.exception;

public class ExamBoardSubjectMismatchException extends RuntimeException {
    public ExamBoardSubjectMismatchException(Long examBoardId, Long enrollmentId) {
        super("La cursada " + enrollmentId + " no es de la misma materia que la mesa " + examBoardId);
    }
}
