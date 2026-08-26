package com.red_escuela.backend.exception;

public class InstitutionNotFoundException extends RuntimeException {
    public InstitutionNotFoundException(Long id) {
        super("Institution no encontrada: " + id);
    }
}
