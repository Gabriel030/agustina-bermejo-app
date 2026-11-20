package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TemplateNothingToPublishException extends ConflictException {

    public TemplateNothingToPublishException() {
        super("No hay cambios para publicar");
    }
}
