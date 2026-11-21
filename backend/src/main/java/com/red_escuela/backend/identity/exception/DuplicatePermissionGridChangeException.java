package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class DuplicatePermissionGridChangeException extends UnprocessableEntityException {
    public DuplicatePermissionGridChangeException(String role, String permissionCode) {
        super("El mismo cambio de la grilla aparece más de una vez: " + role + " / " + permissionCode);
    }
}
