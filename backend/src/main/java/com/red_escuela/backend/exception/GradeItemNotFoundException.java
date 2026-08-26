package com.red_escuela.backend.exception;

public class GradeItemNotFoundException extends RuntimeException {
    public GradeItemNotFoundException(Long id) {
        super("Nota no encontrada: " + id);
    }
}
