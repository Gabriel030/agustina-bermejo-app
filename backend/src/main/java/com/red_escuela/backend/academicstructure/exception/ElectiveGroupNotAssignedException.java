package com.red_escuela.backend.academicstructure.exception;

/**
 * Una carrera tiene que tener rango (ProgramElectiveGroup) asignado a un ElectiveGroup
 * antes de poder taguear una materia de esa carrera con ese grupo (Spec Estructura
 * académica, decisión 8) — evita electivas "huérfanas" sin mínimo/máximo definido.
 */
public class ElectiveGroupNotAssignedException extends RuntimeException {
    public ElectiveGroupNotAssignedException(Long programId, Long electiveGroupId) {
        super("El grupo electivo " + electiveGroupId + " no tiene rango asignado a la carrera " + programId);
    }
}
