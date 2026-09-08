package com.red_escuela.backend.academicstructure.exception;

public class ElectiveGroupAlreadyExistsException extends RuntimeException {
    public ElectiveGroupAlreadyExistsException(String name) {
        super("Ya existe un ElectiveGroup con nombre: " + name);
    }
}
