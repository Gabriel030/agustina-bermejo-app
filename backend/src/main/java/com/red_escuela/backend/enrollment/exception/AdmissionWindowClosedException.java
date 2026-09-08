package com.red_escuela.backend.enrollment.exception;

public class AdmissionWindowClosedException extends RuntimeException {
    public AdmissionWindowClosedException(String message) {
        super(message);
    }
}
