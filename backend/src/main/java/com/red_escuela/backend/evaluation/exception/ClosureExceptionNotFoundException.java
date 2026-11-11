package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ClosureExceptionNotFoundException extends NotFoundException {
    public ClosureExceptionNotFoundException(Long enrollmentId) {
        super("La inscripción " + enrollmentId + " no tiene una excepción vigente en este cierre");
    }
}
