package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ElectiveGroupAlreadyExistsException extends ConflictException {
    public ElectiveGroupAlreadyExistsException(String name) {
        super("Ya existe un ElectiveGroup con nombre: " + name);
    }
}
