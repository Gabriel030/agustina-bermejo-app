package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class AmbiguousSectionException extends BadRequestException {
    public AmbiguousSectionException(Long courseSubjectId) {
        super("El módulo " + courseSubjectId + " tiene más de una comisión en el Term del curso "
                + "y no se indicó cuál elegir (sectionOverrides)");
    }
}
