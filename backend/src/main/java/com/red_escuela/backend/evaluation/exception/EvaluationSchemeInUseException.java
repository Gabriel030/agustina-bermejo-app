package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EvaluationSchemeInUseException extends ConflictException {
    public EvaluationSchemeInUseException(Long id) {
        super("El esquema de evaluación " + id + " ya está en uso y no se puede editar");
    }
}
