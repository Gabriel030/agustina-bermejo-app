package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramCohortNotFoundException extends NotFoundException {
    public ProgramCohortNotFoundException(Long id) {
        super("Program cohort not found: " + id);
    }
}
