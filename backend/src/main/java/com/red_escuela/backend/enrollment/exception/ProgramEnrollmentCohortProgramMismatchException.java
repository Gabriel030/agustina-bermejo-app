package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramEnrollmentCohortProgramMismatchException extends ConflictException {
    public ProgramEnrollmentCohortProgramMismatchException(Long cohortId, Long programId) {
        super("La cohorte " + cohortId + " no pertenece a la carrera " + programId);
    }
}
