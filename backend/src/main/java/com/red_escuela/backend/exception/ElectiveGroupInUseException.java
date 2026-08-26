package com.red_escuela.backend.exception;

/**
 * No se puede quitar el rango de un ElectiveGroup de una carrera mientras alguna
 * ProgramSubject de esa carrera siga tageada con ese grupo.
 */
public class ElectiveGroupInUseException extends RuntimeException {
    public ElectiveGroupInUseException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " todavía está en uso por materias de la carrera " + programId);
    }
}
