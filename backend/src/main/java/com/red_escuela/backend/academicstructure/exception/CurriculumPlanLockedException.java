package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CurriculumPlanLockedException extends ConflictException {
    public CurriculumPlanLockedException(Long planId) {
        super("El plan de estudios " + planId + " tiene alumnos matriculados: crear una versión nueva en lugar de editarlo");
    }
}
