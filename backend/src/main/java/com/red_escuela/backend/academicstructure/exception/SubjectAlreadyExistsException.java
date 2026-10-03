package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectAlreadyExistsException extends ConflictException {
    public SubjectAlreadyExistsException(String name) {
        super("Ya existe un Subject con nombre: " + name);
    }
}
