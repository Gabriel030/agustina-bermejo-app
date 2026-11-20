package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TemplateNothingToDiscardException extends ConflictException {

    public TemplateNothingToDiscardException() {
        super("No hay cambios para descartar");
    }
}
