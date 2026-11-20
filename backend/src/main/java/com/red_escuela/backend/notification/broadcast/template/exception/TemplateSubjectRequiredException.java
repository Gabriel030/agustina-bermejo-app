package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class TemplateSubjectRequiredException extends BadRequestException {

    public TemplateSubjectRequiredException() {
        super("Email templates require a subject");
    }
}
