package com.red_escuela.backend.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String name) {
        super("Role no encontrado: " + name);
    }
}
