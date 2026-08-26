package com.red_escuela.backend.exception;

public class AdmissionWindowClosedException extends RuntimeException {
    public AdmissionWindowClosedException(String message) {
        super(message);
    }
}
