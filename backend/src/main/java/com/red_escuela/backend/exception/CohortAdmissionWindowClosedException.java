package com.red_escuela.backend.exception;

public class CohortAdmissionWindowClosedException extends RuntimeException {
    public CohortAdmissionWindowClosedException(Long programCohortId) {
        super("Admission window is closed for program cohort: " + programCohortId);
    }
}
