package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ProgramEnrollmentCohortRequiredException extends BadRequestException {
    public ProgramEnrollmentCohortRequiredException(Long programId) {
        super("La matriculación a la carrera " + programId + " requiere una cohorte");
    }
}
