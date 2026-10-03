package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidGraduationRequestStateException extends ConflictException {
    public InvalidGraduationRequestStateException(Long id) {
        super("El trámite de egreso " + id + " no está en el estado esperado para esta operación");
    }
}
