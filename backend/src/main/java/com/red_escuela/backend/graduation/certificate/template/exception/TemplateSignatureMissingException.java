package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class TemplateSignatureMissingException extends UnprocessableEntityException {

    public TemplateSignatureMissingException(String signatoryName) {
        super("El firmante " + signatoryName + " no tiene la firma cargada");
    }

    public TemplateSignatureMissingException() {
        super("La plantilla necesita al menos un firmante con la firma cargada");
    }
}
