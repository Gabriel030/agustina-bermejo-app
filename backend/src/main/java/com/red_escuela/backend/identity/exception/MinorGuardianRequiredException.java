package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class MinorGuardianRequiredException extends BadRequestException {
    public MinorGuardianRequiredException() {
        super("Si sos menor de 18 años tenés que cargar nombre, correo y vínculo de un responsable");
    }
}
