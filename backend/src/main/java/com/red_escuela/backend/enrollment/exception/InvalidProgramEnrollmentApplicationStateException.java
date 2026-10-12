package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;

public class InvalidProgramEnrollmentApplicationStateException extends ConflictException {
    public InvalidProgramEnrollmentApplicationStateException(Long id, ProgramEnrollmentApplicationStatus status) {
        super("La solicitud " + id + " ya fue decidida (estado actual: " + status + ")");
    }
}
