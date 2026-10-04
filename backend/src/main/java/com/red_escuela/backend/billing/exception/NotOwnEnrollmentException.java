package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotOwnEnrollmentException extends ForbiddenException {
    public NotOwnEnrollmentException(Long studentId, Long enrollmentId) {
        super("Student " + studentId + " does not own enrollment " + enrollmentId);
    }
}
