package com.red_escuela.backend.evaluation.exception;

public class MaxExamAttemptsExceededException extends RuntimeException {
    public MaxExamAttemptsExceededException(Long subjectId, int maxAttempts) {
        super("Se alcanzó el tope de " + maxAttempts + " intentos de final para la materia " + subjectId);
    }
}
