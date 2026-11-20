package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TermHasActiveEnrollmentsException extends ConflictException {
    public TermHasActiveEnrollmentsException(Long termId) {
        super("No se puede desactivar el término " + termId + " porque tiene inscripciones activas o en espera");
    }
}
