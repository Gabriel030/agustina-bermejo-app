package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class TemplateUnknownMarkerException extends BadRequestException {

    public TemplateUnknownMarkerException(String marker) {
        super("La plantilla usa un marcador desconocido: " + marker);
    }
}
