package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureAlreadyClosedException extends ConflictException {
    public ClosureAlreadyClosedException(Long closureId) {
        super("El cierre " + closureId + " ya está cerrado");
    }
}
