package com.red_escuela.backend.academicstructure.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CurriculumPlanNotOpenForNewStudentsException extends ConflictException {
    public CurriculumPlanNotOpenForNewStudentsException(Long planId) {
        super("El plan de estudios " + planId + " no admite alumnos nuevos");
    }
}
