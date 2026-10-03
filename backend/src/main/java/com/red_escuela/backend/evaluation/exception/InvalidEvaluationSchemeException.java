package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidEvaluationSchemeException extends BadRequestException {
    public InvalidEvaluationSchemeException(String reason) {
        super("Esquema de evaluación inválido: " + reason);
    }
}
