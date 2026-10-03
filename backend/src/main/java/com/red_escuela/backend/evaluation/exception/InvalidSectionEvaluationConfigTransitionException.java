package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;

public class InvalidSectionEvaluationConfigTransitionException extends BadRequestException {
    public InvalidSectionEvaluationConfigTransitionException(SectionEvaluationConfigStatus current, String action) {
        super("No se puede " + action + " una configuración en estado " + current);
    }
}
