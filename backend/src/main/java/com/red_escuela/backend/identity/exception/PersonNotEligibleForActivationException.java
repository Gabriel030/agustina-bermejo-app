package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PersonNotEligibleForActivationException extends ConflictException {
    public PersonNotEligibleForActivationException(Long personId) {
        super("La persona " + personId + " no es elegible para activar su cuenta");
    }
}
