package com.red_escuela.backend.exception;

/**
 * No debería pasar en la práctica: los 5 roles vienen seedeados por V5__role_entity.sql.
 * Si pasa, es preferible un 404 explícito a un 500 silencioso.
 */
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String name) {
        super("Role no encontrado: " + name);
    }
}
