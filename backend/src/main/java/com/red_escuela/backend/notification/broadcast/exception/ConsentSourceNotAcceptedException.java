package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ConsentSourceNotAcceptedException extends BadRequestException {

    public ConsentSourceNotAcceptedException(String source) {
        super("Consent source " + source + " is not accepted here");
    }
}
