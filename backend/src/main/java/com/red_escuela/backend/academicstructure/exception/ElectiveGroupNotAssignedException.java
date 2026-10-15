package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ElectiveGroupNotAssignedException extends BadRequestException {
    public ElectiveGroupNotAssignedException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " no tiene rango asignado a la carrera " + programId);
    }
}
