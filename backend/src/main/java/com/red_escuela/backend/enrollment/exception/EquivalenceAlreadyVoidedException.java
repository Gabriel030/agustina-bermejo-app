package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EquivalenceAlreadyVoidedException extends ConflictException {
    public EquivalenceAlreadyVoidedException(Long equivalenceId) {
        super("La equivalencia " + equivalenceId + " ya fue anulada");
    }
}
