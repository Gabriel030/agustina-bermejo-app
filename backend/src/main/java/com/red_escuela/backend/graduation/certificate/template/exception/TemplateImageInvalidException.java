package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class TemplateImageInvalidException extends UnprocessableEntityException {

    public TemplateImageInvalidException() {
        super("La imagen debe ser un PNG de hasta 2 MB");
    }
}
