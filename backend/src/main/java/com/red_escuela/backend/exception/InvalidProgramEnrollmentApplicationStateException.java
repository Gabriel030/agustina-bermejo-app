package com.red_escuela.backend.exception;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;

public class InvalidProgramEnrollmentApplicationStateException extends RuntimeException {
    public InvalidProgramEnrollmentApplicationStateException(Long id, ProgramEnrollmentApplicationStatus status) {
        super("La solicitud " + id + " ya fue decidida (estado actual: " + status + ")");
    }
}
