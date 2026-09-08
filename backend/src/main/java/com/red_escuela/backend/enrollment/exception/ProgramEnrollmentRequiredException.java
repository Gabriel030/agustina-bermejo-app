package com.red_escuela.backend.enrollment.exception;

public class ProgramEnrollmentRequiredException extends RuntimeException {
    public ProgramEnrollmentRequiredException(Long programEnrollmentId) {
        super("La matriculación a carrera " + programEnrollmentId + " no está ENROLLED/PAUSED");
    }
}
