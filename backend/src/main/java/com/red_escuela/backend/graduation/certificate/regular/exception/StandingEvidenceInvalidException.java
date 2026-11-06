package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StandingEvidenceInvalidException extends BadRequestException {

    public StandingEvidenceInvalidException() {
        super("La evidencia del pago debe tener entre 10 y 500 caracteres");
    }
}
