package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class OwnExceptionDecisionException extends ConflictException {

    public OwnExceptionDecisionException() {
        super("El Director no puede decidir una excepción que él mismo pidió");
    }
}
