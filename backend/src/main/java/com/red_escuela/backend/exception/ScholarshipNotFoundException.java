package com.red_escuela.backend.exception;

public class ScholarshipNotFoundException extends RuntimeException {
    public ScholarshipNotFoundException(Long id) {
        super("Scholarship " + id + " not found");
    }
}
