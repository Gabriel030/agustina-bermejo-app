package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionModificationNotPendingException extends ConflictException {
    public SectionModificationNotPendingException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene un pedido de modificación pendiente de aprobación");
    }
}
