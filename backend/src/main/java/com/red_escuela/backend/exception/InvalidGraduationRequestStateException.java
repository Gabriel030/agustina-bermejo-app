package com.red_escuela.backend.exception;

public class InvalidGraduationRequestStateException extends RuntimeException {
    public InvalidGraduationRequestStateException(Long id) {
        super("El trámite de egreso " + id + " no está en el estado esperado para esta operación");
    }
}
