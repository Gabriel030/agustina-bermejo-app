package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureAlreadyConfirmedException extends ConflictException {
    public ClosureAlreadyConfirmedException(Long closureId) {
        super("El cierre " + closureId + " ya fue confirmado por el docente");
    }
}
