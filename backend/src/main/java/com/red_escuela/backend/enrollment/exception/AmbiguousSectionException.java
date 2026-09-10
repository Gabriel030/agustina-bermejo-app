package com.red_escuela.backend.enrollment.exception;

public class AmbiguousSectionException extends RuntimeException {
    public AmbiguousSectionException(Long courseSubjectId) {
        super("El módulo " + courseSubjectId + " tiene más de una comisión en el Term del curso "
                + "y no se indicó cuál elegir (sectionOverrides)");
    }
}
