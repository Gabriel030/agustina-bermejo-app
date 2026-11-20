package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class TemplateImageInvalidException extends BadRequestException {

    public TemplateImageInvalidException() {
        super("La imagen debe ser un PNG de hasta 1 MB");
    }
}
