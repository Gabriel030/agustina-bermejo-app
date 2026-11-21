package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class DuplicatePermissionModeChangeException extends UnprocessableEntityException {
    public DuplicatePermissionModeChangeException(String role, String module) {
        super("El mismo cambio de modo aparece más de una vez: " + role + " / " + module);
    }
}
