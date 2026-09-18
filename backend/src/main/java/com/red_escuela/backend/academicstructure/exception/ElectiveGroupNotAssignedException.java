package com.red_escuela.backend.academicstructure.exception;

public class ElectiveGroupNotAssignedException extends RuntimeException {
    public ElectiveGroupNotAssignedException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " no tiene rango asignado a la carrera " + programId);
    }
}
