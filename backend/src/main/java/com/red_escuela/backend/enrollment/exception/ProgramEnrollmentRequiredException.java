package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ProgramEnrollmentRequiredException extends BadRequestException {
    public ProgramEnrollmentRequiredException(Long programEnrollmentId) {
        super("La matriculación a carrera " + programEnrollmentId + " no está ENROLLED/PAUSED");
    }
}
