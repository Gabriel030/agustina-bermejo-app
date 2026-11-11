package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureNotReopenableException extends ConflictException {
    public ClosureNotReopenableException(Long closureId) {
        super("El cierre " + closureId + " no está cerrado: solo se puede reabrir un acta cerrada");
    }
}
