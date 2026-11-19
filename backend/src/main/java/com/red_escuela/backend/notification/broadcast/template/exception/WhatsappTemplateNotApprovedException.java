package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class WhatsappTemplateNotApprovedException extends ConflictException implements ReportsErrorCode {

    public WhatsappTemplateNotApprovedException(Long messageTemplateId) {
        super("The message template " + messageTemplateId + " has no approved WhatsApp template");
    }
}
