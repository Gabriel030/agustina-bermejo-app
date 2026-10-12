package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IncompleteSectionSetupException extends ConflictException {
    public IncompleteSectionSetupException(Long courseSubjectId) {
        super("El módulo " + courseSubjectId + " no tiene ninguna comisión creada en el Term del curso");
    }
}
