package com.red_escuela.backend.shared.domain;

public class InvalidEmailAddressException extends IllegalArgumentException {

    public InvalidEmailAddressException() {
        super("Email address format is invalid");
    }
}
