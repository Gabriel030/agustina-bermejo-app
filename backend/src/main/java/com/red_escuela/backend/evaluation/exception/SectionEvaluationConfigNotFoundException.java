package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SectionEvaluationConfigNotFoundException extends NotFoundException {
    public SectionEvaluationConfigNotFoundException(Long sectionId) {
        super("No existe configuración de evaluación para la comisión " + sectionId);
    }
}
