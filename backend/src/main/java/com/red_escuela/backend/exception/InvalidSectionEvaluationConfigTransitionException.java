package com.red_escuela.backend.exception;

import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;

public class InvalidSectionEvaluationConfigTransitionException extends RuntimeException {
    public InvalidSectionEvaluationConfigTransitionException(SectionEvaluationConfigStatus current, String action) {
        super("No se puede " + action + " una configuración en estado " + current);
    }
}
