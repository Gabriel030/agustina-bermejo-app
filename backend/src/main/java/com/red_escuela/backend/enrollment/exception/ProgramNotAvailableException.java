package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramNotAvailableException extends ConflictException {
    public ProgramNotAvailableException(Long programId) {
        super("El program " + programId + " no está disponible en el catálogo para solicitar inscripción");
    }
}
