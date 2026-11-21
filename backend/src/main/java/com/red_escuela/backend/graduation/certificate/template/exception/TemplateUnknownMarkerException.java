package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class TemplateUnknownMarkerException extends UnprocessableEntityException {

    public TemplateUnknownMarkerException(String marker) {
        super("La plantilla usa un marcador desconocido: " + marker);
    }
}
