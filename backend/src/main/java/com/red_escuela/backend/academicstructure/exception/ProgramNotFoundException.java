package com.red_escuela.backend.academicstructure.exception;

public class ProgramNotFoundException extends RuntimeException {
    public ProgramNotFoundException(Long id) {
        super("Program no encontrado: " + id);
    }
}
