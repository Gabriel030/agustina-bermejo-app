package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramEnrollmentPlanMismatchException extends ConflictException {
    public ProgramEnrollmentPlanMismatchException(Long planId, Long programId) {
        super("El plan de estudios " + planId + " no pertenece a la carrera " + programId);
    }
}
