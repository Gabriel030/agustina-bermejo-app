package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidTransitionConfirmationException extends ConflictException {
    public InvalidTransitionConfirmationException() {
        super("La confirmación de la vista previa es inválida, venció o corresponde a otro estado; pedí una vista previa nueva");
    }
}
