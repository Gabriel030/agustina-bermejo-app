package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StandingEvidenceInvalidException extends BadRequestException {

    public StandingEvidenceInvalidException() {
        super("La nota de evidencia no puede superar los 500 caracteres");
    }
}
