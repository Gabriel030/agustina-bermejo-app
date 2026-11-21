package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CertificateExceptionReasonInvalidException extends BadRequestException {

    public CertificateExceptionReasonInvalidException() {
        super("El motivo de la excepción debe tener entre 20 y 1000 caracteres");
    }
}
