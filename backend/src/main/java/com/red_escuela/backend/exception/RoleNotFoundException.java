package com.red_escuela.backend.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class RoleNotFoundException extends NotFoundException {
    public RoleNotFoundException(String name) {
        super("Role no encontrado: " + name);
    }
}
