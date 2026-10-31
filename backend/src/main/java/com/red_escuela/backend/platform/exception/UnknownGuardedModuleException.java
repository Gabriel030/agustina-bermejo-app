package com.red_escuela.backend.platform.exception;

public class UnknownGuardedModuleException extends IllegalStateException {
    public UnknownGuardedModuleException(String module) {
        super("@RequiresModule apunta a un módulo que no existe: " + module);
    }
}
