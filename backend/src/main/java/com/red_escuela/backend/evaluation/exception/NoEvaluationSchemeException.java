package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class NoEvaluationSchemeException extends BadRequestException {
    public NoEvaluationSchemeException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene EvaluationScheme asignado");
    }
}
