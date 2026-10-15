package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidEmailAddressException extends BadRequestException {

    public InvalidEmailAddressException() {
        super("Email address format is invalid");
    }
}
