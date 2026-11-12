package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NotOwnEnrollmentException extends NotFoundException {
    public NotOwnEnrollmentException(Long studentId, Long enrollmentId) {
        super("Student " + studentId + " does not own enrollment " + enrollmentId);
    }
}
