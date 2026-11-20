package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GuardianConfirmationNotApplicableException extends ConflictException {
    public GuardianConfirmationNotApplicableException() {
        super("No hay una confirmación de responsable pendiente para tu cuenta");
    }
}
