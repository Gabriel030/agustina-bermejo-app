package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class UsernameAlreadyExistsException extends ConflictException {
    public UsernameAlreadyExistsException() {
        super("Ya existe una cuenta con esos datos");
    }
}
