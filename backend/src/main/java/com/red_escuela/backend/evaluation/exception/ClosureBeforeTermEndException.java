package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureBeforeTermEndException extends ConflictException {
    public ClosureBeforeTermEndException(Long sectionId) {
        super("La comisión " + sectionId + " todavía no terminó su término: no se puede cerrar");
    }
}
