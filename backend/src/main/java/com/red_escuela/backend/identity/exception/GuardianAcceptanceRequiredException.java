package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GuardianAcceptanceRequiredException extends ConflictException {
    public GuardianAcceptanceRequiredException() {
        super("La aceptación de una persona menor de edad la confirma su responsable desde el correo que le enviamos");
    }
}
