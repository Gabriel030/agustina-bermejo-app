package com.red_escuela.backend.academicstructure.exception;

public class CycleIncompleteException extends RuntimeException {
    public CycleIncompleteException(String reason) {
        super("Ciclo incompleto: " + reason);
    }
}
