package com.red_escuela.backend.evaluation.exception;

public class EvaluationSchemeInUseException extends RuntimeException {
    public EvaluationSchemeInUseException(Long id) {
        super("El esquema de evaluación " + id + " ya está en uso y no se puede editar");
    }
}
