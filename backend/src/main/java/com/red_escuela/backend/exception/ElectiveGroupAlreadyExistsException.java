package com.red_escuela.backend.exception;

public class ElectiveGroupAlreadyExistsException extends RuntimeException {
    public ElectiveGroupAlreadyExistsException(String name) {
        super("Ya existe un ElectiveGroup con nombre: " + name);
    }
}
