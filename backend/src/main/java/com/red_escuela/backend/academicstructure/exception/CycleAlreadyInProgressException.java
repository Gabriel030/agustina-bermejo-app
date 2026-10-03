package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CycleAlreadyInProgressException extends ConflictException {
    public CycleAlreadyInProgressException() {
        super("Ya existe un ciclo en borrador o pendiente de aprobación para este programa/curso en el período seleccionado");
    }
}
