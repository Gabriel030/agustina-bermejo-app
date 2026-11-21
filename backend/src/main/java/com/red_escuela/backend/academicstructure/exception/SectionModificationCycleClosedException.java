package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionModificationCycleClosedException extends ConflictException {
    public SectionModificationCycleClosedException(Long sectionId) {
        super("El ciclo de la comisión " + sectionId + " está cerrado y no admite pedidos de modificación de horario");
    }
}
