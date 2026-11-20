package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class WhatsappTemplateMappingAlreadyExistsException extends ConflictException implements ReportsErrorCode {

    public WhatsappTemplateMappingAlreadyExistsException(Long messageTemplateId) {
        super("The message template " + messageTemplateId + " already has a WhatsApp template mapping");
    }
}
