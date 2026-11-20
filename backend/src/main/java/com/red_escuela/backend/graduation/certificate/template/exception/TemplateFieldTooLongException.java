package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class TemplateFieldTooLongException extends BadRequestException {

    public TemplateFieldTooLongException(String field) {
        super("El campo de la plantilla supera el largo máximo: " + field);
    }
}
