package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class PreceptorAssignmentTargetInvalidException extends BadRequestException {
    public PreceptorAssignmentTargetInvalidException() {
        super("Hay que indicar exactamente una comisión: de carrera o de curso");
    }
}
