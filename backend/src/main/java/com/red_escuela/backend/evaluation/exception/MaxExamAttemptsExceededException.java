package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class MaxExamAttemptsExceededException extends BadRequestException {
    public MaxExamAttemptsExceededException(Long subjectId, int maxAttempts) {
        super("Se alcanzó el tope de " + maxAttempts + " intentos de final para la materia " + subjectId);
    }
}
