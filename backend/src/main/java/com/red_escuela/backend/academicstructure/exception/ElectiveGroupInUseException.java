package com.red_escuela.backend.academicstructure.exception;

public class ElectiveGroupInUseException extends RuntimeException {
    public ElectiveGroupInUseException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " todavía está en uso por materias de la carrera " + programId);
    }
}
