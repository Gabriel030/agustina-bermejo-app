package com.red_escuela.backend.evaluation.exception;

public class NoEvaluationSchemeException extends RuntimeException {
    public NoEvaluationSchemeException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene EvaluationScheme asignado");
    }
}
