package com.red_escuela.backend.billing.exception;

public class ScholarshipNotFoundException extends RuntimeException {
    public ScholarshipNotFoundException(Long id) {
        super("Scholarship " + id + " not found");
    }
}
