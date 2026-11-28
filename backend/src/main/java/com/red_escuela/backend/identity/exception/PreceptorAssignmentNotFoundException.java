package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PreceptorAssignmentNotFoundException extends NotFoundException {
    public PreceptorAssignmentNotFoundException(Long id) {
        super("Asignación de preceptor no encontrada: " + id);
    }
}
