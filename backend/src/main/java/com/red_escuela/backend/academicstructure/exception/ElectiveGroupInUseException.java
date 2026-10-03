package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ElectiveGroupInUseException extends ConflictException {
    public ElectiveGroupInUseException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " todavía está en uso por materias de la carrera " + programId);
    }
}
