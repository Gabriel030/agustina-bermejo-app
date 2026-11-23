package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class UnknownPermissionRoleException extends UnprocessableEntityException {
    public UnknownPermissionRoleException(String role) {
        super("El rol no existe en la grilla de permisos: " + role);
    }
}
