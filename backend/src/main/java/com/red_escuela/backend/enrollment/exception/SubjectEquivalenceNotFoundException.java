package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SubjectEquivalenceNotFoundException extends NotFoundException {
    public SubjectEquivalenceNotFoundException(Long equivalenceId) {
        super("Equivalencia no encontrada: " + equivalenceId);
    }
}
