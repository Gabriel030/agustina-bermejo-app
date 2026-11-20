package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TemplateIncompleteException extends ConflictException {

    public TemplateIncompleteException(String field) {
        super("La plantilla está incompleta: falta " + field);
    }
}
