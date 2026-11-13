package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionHasActiveEnrollmentsException extends ConflictException {
    public SectionHasActiveEnrollmentsException(Long sectionId) {
        super("No se puede desactivar la comisión " + sectionId + " porque tiene inscripciones activas o en espera");
    }
}
