package com.red_escuela.backend.evaluation.exception;

public class EvaluationSchemeNotFoundException extends RuntimeException {
    public EvaluationSchemeNotFoundException(Long id) {
        super("EvaluationScheme no encontrado: " + id);
    }
}
