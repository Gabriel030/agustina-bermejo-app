package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class DirectorPermissionsNotEditableException extends UnprocessableEntityException {
    public DirectorPermissionsNotEditableException() {
        super("El Director conserva todos los permisos y su fila no se puede editar");
    }
}
