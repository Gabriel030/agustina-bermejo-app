package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class UnknownPermissionModuleException extends UnprocessableEntityException {
    public UnknownPermissionModuleException(String module) {
        super("El módulo no existe en la grilla de permisos: " + module);
    }
}
