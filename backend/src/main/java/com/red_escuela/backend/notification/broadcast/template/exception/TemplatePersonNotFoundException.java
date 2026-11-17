package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class TemplatePersonNotFoundException extends UnprocessableEntityException {

    public TemplatePersonNotFoundException(Long personId) {
        super("Person " + personId + " referenced by the template preview does not exist");
    }
}
