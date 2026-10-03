package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CycleIncompleteException extends BadRequestException {
    public CycleIncompleteException(String reason) {
        super("Ciclo incompleto: " + reason);
    }
}
