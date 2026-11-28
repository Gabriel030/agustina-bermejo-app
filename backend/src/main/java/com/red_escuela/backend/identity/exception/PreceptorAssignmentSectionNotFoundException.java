package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PreceptorAssignmentSectionNotFoundException extends NotFoundException {
    public PreceptorAssignmentSectionNotFoundException(Long sectionId) {
        super("Comisión no encontrada: " + sectionId);
    }
}
