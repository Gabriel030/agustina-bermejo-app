package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class TemplateIncompleteException extends UnprocessableEntityException {

    public TemplateIncompleteException(String field) {
        super("La plantilla está incompleta: falta " + field);
    }
}
