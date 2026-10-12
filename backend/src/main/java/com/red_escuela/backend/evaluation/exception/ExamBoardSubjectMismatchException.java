package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ExamBoardSubjectMismatchException extends BadRequestException {
    public ExamBoardSubjectMismatchException(Long examBoardId, Long enrollmentId) {
        super("La cursada " + enrollmentId + " no es de la misma materia que la mesa " + examBoardId);
    }
}
