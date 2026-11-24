package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EnrollmentNotActiveException extends ConflictException {
    public EnrollmentNotActiveException(Long programEnrollmentId) {
        super("La matriculación " + programEnrollmentId + " no está activa");
    }
}
