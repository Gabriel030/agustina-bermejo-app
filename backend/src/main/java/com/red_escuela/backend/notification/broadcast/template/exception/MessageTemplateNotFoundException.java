package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class MessageTemplateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public MessageTemplateNotFoundException(Long id) {
        super("Message template " + id + " not found");
    }
}
