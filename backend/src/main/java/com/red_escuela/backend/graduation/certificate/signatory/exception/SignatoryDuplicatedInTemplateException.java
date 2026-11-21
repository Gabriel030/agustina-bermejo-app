package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class SignatoryDuplicatedInTemplateException extends UnprocessableEntityException {

    public SignatoryDuplicatedInTemplateException() {
        super("El firmante 1 y el firmante 2 deben ser personas distintas");
    }
}
