package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PersonAlreadyActivatedException extends ConflictException {
    public PersonAlreadyActivatedException(Long personId) {
        super("La persona " + personId + " ya tiene contraseña");
    }
}
