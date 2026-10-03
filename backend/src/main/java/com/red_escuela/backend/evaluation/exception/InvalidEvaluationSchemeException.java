package com.red_escuela.backend.evaluation.exception;

public class InvalidEvaluationSchemeException extends RuntimeException {
    public InvalidEvaluationSchemeException(String reason) {
        super("Esquema de evaluación inválido: " + reason);
    }
}
