package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidBillingModeTransitionException extends ConflictException {
    private InvalidBillingModeTransitionException(String message) {
        super(message);
    }

    public static InvalidBillingModeTransitionException notAllowed(BillingMode from, BillingMode to) {
        return new InvalidBillingModeTransitionException("El modo de facturación no puede pasar de " + from + " a " + to);
    }

    public static InvalidBillingModeTransitionException cutoverRequired(BillingMode from, BillingMode to) {
        return new InvalidBillingModeTransitionException("Pasar de " + from + " a " + to + " exige completar el corte");
    }

    public static InvalidBillingModeTransitionException preflightRequired(BillingMode from, BillingMode to) {
        return new InvalidBillingModeTransitionException("Pasar de " + from + " a " + to + " exige aprobar la verificación previa");
    }
}
