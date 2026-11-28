package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PreceptorAssignmentAlreadyExistsException extends ConflictException {
    public PreceptorAssignmentAlreadyExistsException(Long personId) {
        super("La persona " + personId + " ya está asignada como preceptor a esa comisión");
    }
}
