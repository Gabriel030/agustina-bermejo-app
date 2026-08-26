package com.red_escuela.backend.exception;

public class TermNotFoundException extends RuntimeException {
    public TermNotFoundException(Long id) {
        super("Term no encontrado: " + id);
    }
}
