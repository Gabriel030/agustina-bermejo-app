package com.red_escuela.backend.exception;

public class RoleNotAssignableByActorException extends RuntimeException {
    public RoleNotAssignableByActorException(String role) {
        super("Tu rol no puede dar de alta/editar personal con rol " + role);
    }
}
