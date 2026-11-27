package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectNotInEnrollmentPlanException extends ConflictException {
    public SubjectNotInEnrollmentPlanException(Long subjectId, Long planId) {
        super("La materia " + subjectId + " no está en el plan de estudios " + planId + " de la matriculación");
    }
}
