package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ScholarshipNotFoundException extends NotFoundException {
    public ScholarshipNotFoundException(Long id) {
        super("Scholarship " + id + " not found");
    }
}
