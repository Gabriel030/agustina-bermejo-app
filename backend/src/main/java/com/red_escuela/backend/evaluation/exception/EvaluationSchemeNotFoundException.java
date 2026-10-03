package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class EvaluationSchemeNotFoundException extends NotFoundException {
    public EvaluationSchemeNotFoundException(Long id) {
        super("EvaluationScheme no encontrado: " + id);
    }
}
