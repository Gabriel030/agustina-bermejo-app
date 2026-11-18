package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ConsentCategoryRepeatedException extends BadRequestException {

    public ConsentCategoryRepeatedException(String category) {
        super("Consent category " + category + " is repeated");
    }
}
