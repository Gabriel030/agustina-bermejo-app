package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class SensitivePermissionConfirmationRequiredException extends UnprocessableEntityException {
    public SensitivePermissionConfirmationRequiredException(String permissionCode) {
        super("Cambiar un permiso sensible exige una confirmación reforzada: " + permissionCode);
    }
}
