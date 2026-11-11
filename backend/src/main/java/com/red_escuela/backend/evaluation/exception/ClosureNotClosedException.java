package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureNotClosedException extends ConflictException {
    public ClosureNotClosedException(Long closureId) {
        super("El cierre " + closureId + " todavía no está cerrado: el acta se descarga después del cierre");
    }
}
