package com.red_escuela.backend.enrollment.exception;

public class TermEnrollmentOverrideClosedException extends RuntimeException {
    public TermEnrollmentOverrideClosedException(Long termId) {
        super("La ventana de inscripción del Term " + termId + " está cerrada por override manual");
    }
}
