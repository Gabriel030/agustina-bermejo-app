package com.red_escuela.backend.exception;

public class ProgramNotFoundException extends RuntimeException {
    public ProgramNotFoundException(Long id) {
        super("Program no encontrado: " + id);
    }
}
