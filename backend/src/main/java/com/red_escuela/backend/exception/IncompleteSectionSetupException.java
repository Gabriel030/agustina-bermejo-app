package com.red_escuela.backend.exception;

public class IncompleteSectionSetupException extends RuntimeException {
    public IncompleteSectionSetupException(Long courseSubjectId) {
        super("El módulo " + courseSubjectId + " no tiene ninguna comisión creada en el Term del curso");
    }
}
