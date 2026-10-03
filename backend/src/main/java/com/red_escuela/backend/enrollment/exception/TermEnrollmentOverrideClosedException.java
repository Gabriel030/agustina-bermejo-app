package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TermEnrollmentOverrideClosedException extends ConflictException {
    public TermEnrollmentOverrideClosedException(Long termId) {
        super("La ventana de inscripción del Term " + termId + " está cerrada por override manual");
    }
}
