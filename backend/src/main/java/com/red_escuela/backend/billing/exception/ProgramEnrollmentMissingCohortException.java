package com.red_escuela.backend.billing.exception;

public class ProgramEnrollmentMissingCohortException extends RuntimeException {
    public ProgramEnrollmentMissingCohortException(Long programEnrollmentId) {
        super("Program enrollment " + programEnrollmentId + " has no cohort assigned — cannot generate its enrollment fee");
    }
}
