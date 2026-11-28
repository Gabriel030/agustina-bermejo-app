package com.red_escuela.backend.academicstructure.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CurriculumPlanWithoutSubjectsException extends ConflictException {
    public CurriculumPlanWithoutSubjectsException(Long planId) {
        super("El plan de estudios " + planId + " no tiene materias: no se puede activar");
    }
}
