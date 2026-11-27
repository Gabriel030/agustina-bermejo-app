package com.red_escuela.backend.academicstructure.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidCurriculumPlanTransitionException extends ConflictException {
    public InvalidCurriculumPlanTransitionException(Long planId, String from, String to) {
        super("El plan de estudios " + planId + " no puede pasar de " + from + " a " + to);
    }
}
