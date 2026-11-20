package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CertificateExceptionDecisionReasonInvalidException extends BadRequestException {

    public CertificateExceptionDecisionReasonInvalidException() {
        super("El motivo de la decisión debe tener entre 10 y 500 caracteres");
    }
}
