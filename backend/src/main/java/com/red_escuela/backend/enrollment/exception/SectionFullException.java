package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionFullException extends ConflictException {
    public SectionFullException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene cupo disponible");
    }
}
