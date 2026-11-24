package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class SubjectNotInPlanException extends UnprocessableEntityException {
    public SubjectNotInPlanException(Long subjectId, Long programId) {
        super("La materia " + subjectId + " no está en el plan vigente de la carrera " + programId);
    }
}
