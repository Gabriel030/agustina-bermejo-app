package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionClosureClosedException extends ConflictException {
    public SectionClosureClosedException(Long sectionId) {
        super("La comisión " + sectionId + " tiene el cierre cerrado: para corregir hay que reabrir el cierre");
    }
}
