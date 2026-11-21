package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class UnknownPermissionCodeException extends UnprocessableEntityException {
    public UnknownPermissionCodeException(String permissionCode) {
        super("El permiso no existe en el catálogo: " + permissionCode);
    }
}
