package com.red_escuela.backend.exception;

public class EvaluationSchemeNotFoundException extends RuntimeException {
    public EvaluationSchemeNotFoundException(Long id) {
        super("EvaluationScheme no encontrado: " + id);
    }
}
