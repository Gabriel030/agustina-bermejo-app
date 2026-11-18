package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ModuleNotEnabledException extends ConflictException {
    public ModuleNotEnabledException(String module) {
        super("El módulo " + module + " no está habilitado para esta institución");
    }
}
