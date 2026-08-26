package com.red_escuela.backend.exception;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(Long id) {
        super("Person no encontrada: " + id);
    }
}
