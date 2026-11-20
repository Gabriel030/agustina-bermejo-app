package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class TemplateImageNotFoundException extends NotFoundException implements ReportsErrorCode {

    public TemplateImageNotFoundException() {
        super("No existe la imagen de la plantilla");
    }
}
