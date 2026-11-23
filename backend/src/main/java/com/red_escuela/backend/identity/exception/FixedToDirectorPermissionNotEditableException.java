package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class FixedToDirectorPermissionNotEditableException extends UnprocessableEntityException {
    public FixedToDirectorPermissionNotEditableException(String permissionCode) {
        super("El permiso " + permissionCode + " es exclusivo del Director y no se puede delegar");
    }
}
