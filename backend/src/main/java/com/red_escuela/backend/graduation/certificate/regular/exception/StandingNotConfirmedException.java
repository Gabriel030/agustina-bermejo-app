package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StandingNotConfirmedException extends BadRequestException {

    public StandingNotConfirmedException() {
        super("Hay que confirmar que el alumno está al día con sus pagos");
    }
}
