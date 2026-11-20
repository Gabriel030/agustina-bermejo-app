package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class WhatsappTemplateMappingNotFoundException extends NotFoundException implements ReportsErrorCode {

    public WhatsappTemplateMappingNotFoundException(Long id) {
        super("WhatsApp template mapping " + id + " not found");
    }
}
