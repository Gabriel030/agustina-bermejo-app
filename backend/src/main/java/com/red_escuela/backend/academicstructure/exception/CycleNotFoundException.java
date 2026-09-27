package com.red_escuela.backend.academicstructure.exception;

public class CycleNotFoundException extends RuntimeException {
    public CycleNotFoundException(Long id) {
        super("Ciclo no encontrado: " + id);
    }
}
