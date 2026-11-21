package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class PermissionFixedToDirectorException extends ForbiddenException {
    public PermissionFixedToDirectorException(String permissionCode) {
        super("El permiso es fijo del Director y no se puede delegar: " + permissionCode);
    }
}
