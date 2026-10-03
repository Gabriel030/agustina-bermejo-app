package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramEnrollmentMissingCohortException extends ConflictException {
    public ProgramEnrollmentMissingCohortException(Long programEnrollmentId) {
        super("Program enrollment " + programEnrollmentId + " has no cohort assigned — cannot generate its enrollment fee");
    }
}
