package com.red_escuela.backend.exception;

public class SectionEvaluationConfigNotFoundException extends RuntimeException {
    public SectionEvaluationConfigNotFoundException(Long sectionId) {
        super("No existe configuración de evaluación para la comisión " + sectionId);
    }
}
