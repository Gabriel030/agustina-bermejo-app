package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureNotAvailableException extends ConflictException {
    public ClosureNotAvailableException(Long sectionId) {
        super("La comisión " + sectionId + " todavía no tiene notas publicadas para armar el cierre");
    }
}
