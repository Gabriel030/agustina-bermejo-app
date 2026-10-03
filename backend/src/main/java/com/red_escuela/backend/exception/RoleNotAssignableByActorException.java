package com.red_escuela.backend.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class RoleNotAssignableByActorException extends ForbiddenException {
    public RoleNotAssignableByActorException(String role) {
        super("Tu rol no puede dar de alta/editar personal con rol " + role);
    }
}
