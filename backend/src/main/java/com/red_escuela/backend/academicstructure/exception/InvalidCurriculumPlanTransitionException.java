package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidCurriculumPlanTransitionException extends ConflictException {
    public InvalidCurriculumPlanTransitionException(Long planId, CurriculumPlanStatus from, CurriculumPlanStatus to) {
        super("El plan de estudios " + planId + " no puede pasar de " + from + " a " + to);
    }
}
