package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PreceptorRoleRequiredException extends ConflictException {
    public PreceptorRoleRequiredException(Long personId) {
        super("La persona " + personId + " no tiene el rol de preceptor");
    }
}
