package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DirectorAlreadyExistsException extends ConflictException {
    public DirectorAlreadyExistsException() {
        super("El instituto ya tiene un Director");
    }
}
