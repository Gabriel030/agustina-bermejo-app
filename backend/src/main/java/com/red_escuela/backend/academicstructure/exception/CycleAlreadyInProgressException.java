package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CycleAlreadyInProgressException extends ConflictException {
    public CycleAlreadyInProgressException() {
        super("Ya existe un ciclo vigente (en borrador, pendiente o aprobado) para este programa/curso en el período seleccionado");
    }
}
