package com.red_escuela.backend.evaluation.exception;

// Decisión de producto: el tope de reintentos de final es configurable por materia
// (Subject/CourseSubject.maxFinalExamAttempts), no ilimitado ni hardcodeado.
public class MaxExamAttemptsExceededException extends RuntimeException {
    public MaxExamAttemptsExceededException(Long subjectId, int maxAttempts) {
        super("Se alcanzó el tope de " + maxAttempts + " intentos de final para la materia " + subjectId);
    }
}
