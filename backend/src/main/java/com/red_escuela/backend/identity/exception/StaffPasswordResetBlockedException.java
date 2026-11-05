package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StaffPasswordResetBlockedException extends ConflictException {
    public StaffPasswordResetBlockedException(Long personId) {
        super("El reset de contraseña está bloqueado mientras haya un cambio de email pendiente o reciente: personId=" + personId);
    }
}
