package com.red_escuela.backend.exception;

public class ClassSessionNotFoundException extends RuntimeException {
    public ClassSessionNotFoundException(Long id) {
        super("Clase no encontrada: " + id);
    }
}
