package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class LegalAcceptanceRequiredException extends BadRequestException {
    public LegalAcceptanceRequiredException() {
        super("Para registrarte tenés que aceptar los términos y condiciones y la política de privacidad");
    }
}
