package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.util.List;

public class IncompleteSectionEvaluationConfigException extends ConflictException {
    public IncompleteSectionEvaluationConfigException(List<String> missingFields) {
        super("La configuración de evaluación está incompleta: faltan " + String.join(", ", missingFields));
    }
}
