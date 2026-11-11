package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ClosureNotFoundException extends NotFoundException {
    public ClosureNotFoundException(Long closureId) {
        super("No existe el cierre " + closureId);
    }
}
