package com.red_escuela.backend.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class UsernameAlreadyExistsException extends ConflictException {
    public UsernameAlreadyExistsException(String username) {
        super("Ya existe una cuenta con el usuario " + username);
    }
}
