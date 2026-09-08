package com.red_escuela.backend.billing.exception;

public class ProgramCohortNotFoundException extends RuntimeException {
    public ProgramCohortNotFoundException(Long id) {
        super("Program cohort not found: " + id);
    }
}
