package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureExceptionAlreadyGrantedException extends ConflictException {
    public ClosureExceptionAlreadyGrantedException(Long enrollmentId) {
        super("La inscripción " + enrollmentId + " ya tiene una excepción vigente en este cierre");
    }
}
