package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class PermissionGridForbiddenException extends ForbiddenException {
    public PermissionGridForbiddenException() {
        super("Solo el Director puede editar la grilla de permisos");
    }
}
