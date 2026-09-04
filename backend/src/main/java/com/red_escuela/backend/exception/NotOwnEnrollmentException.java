package com.red_escuela.backend.exception;

public class NotOwnEnrollmentException extends RuntimeException {
    public NotOwnEnrollmentException(Long studentId, Long enrollmentId) {
        super("Student " + studentId + " does not own enrollment " + enrollmentId);
    }
}
