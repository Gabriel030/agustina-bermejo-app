package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CohortAdmissionWindowClosedException extends ConflictException {
    public CohortAdmissionWindowClosedException(Long programCohortId) {
        super("Admission window is closed for program cohort: " + programCohortId);
    }
}
