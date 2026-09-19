package com.red_escuela.backend.identity.exception;

public class DniAlreadyExistsException extends RuntimeException {
    public DniAlreadyExistsException(String dni) {
        super("Ya existe una persona con DNI: " + dni);
    }
}
