package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ConsentCategoryNotOptionalException extends BadRequestException {

    public ConsentCategoryNotOptionalException(String category) {
        super("Consent category " + category + " does not admit an optional consent");
    }
}
