package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionModificationAlreadyPendingException extends ConflictException {
    public SectionModificationAlreadyPendingException(Long sectionId) {
        super("La comisión " + sectionId + " ya tiene un pedido de modificación pendiente de aprobación");
    }
}
