package com.red_escuela.backend.exception;

public class ProgramCohortNotFoundException extends RuntimeException {
    public ProgramCohortNotFoundException(Long id) {
        super("Program cohort not found: " + id);
    }
}
