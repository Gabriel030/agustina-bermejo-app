package com.red_escuela.backend.exception;

public class EnrollmentWindowClosedException extends RuntimeException {
    public EnrollmentWindowClosedException(String message) {
        super(message);
    }
}
