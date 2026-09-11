package com.red_escuela.backend.graduation.exception;

public class NotEligibleForGraduationException extends RuntimeException {
    public NotEligibleForGraduationException(Long programEnrollmentId) {
        super("La matriculación " + programEnrollmentId + " no es elegible para completar la carrera todavía");
    }
}
