package com.red_escuela.backend.academicstructure.exception;

public class CycleAlreadyInProgressException extends RuntimeException {
    public CycleAlreadyInProgressException() {
        super("Ya existe un ciclo en borrador o pendiente de aprobación para este programa/curso en el período seleccionado");
    }
}
