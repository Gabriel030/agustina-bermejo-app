package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StandingEvidenceRequiredException extends BadRequestException {

    public StandingEvidenceRequiredException() {
        super("La institución exige una nota de evidencia para emitir la constancia");
    }
}
