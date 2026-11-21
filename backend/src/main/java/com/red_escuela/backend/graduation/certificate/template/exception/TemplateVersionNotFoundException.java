package com.red_escuela.backend.graduation.certificate.template.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class TemplateVersionNotFoundException extends NotFoundException implements ReportsErrorCode {

    public TemplateVersionNotFoundException() {
        super("No existe esa versión de la plantilla");
    }
}
