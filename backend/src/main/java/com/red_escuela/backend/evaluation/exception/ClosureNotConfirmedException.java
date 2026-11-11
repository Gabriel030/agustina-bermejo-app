package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureNotConfirmedException extends ConflictException {
    public ClosureNotConfirmedException(Long closureId) {
        super("El cierre " + closureId + " todavía no fue confirmado por el docente");
    }
}
