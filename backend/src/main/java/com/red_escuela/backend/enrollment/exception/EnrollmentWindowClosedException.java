package com.red_escuela.backend.enrollment.exception;

public class EnrollmentWindowClosedException extends RuntimeException {
    public EnrollmentWindowClosedException(String message) {
        super(message);
    }
}
