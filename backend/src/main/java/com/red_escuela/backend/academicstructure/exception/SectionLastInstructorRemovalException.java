package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SectionLastInstructorRemovalException extends ConflictException {
    public SectionLastInstructorRemovalException(Long sectionId) {
        super("La comisión " + sectionId + " está activa y este es su único docente titular: asigná otro antes de quitarlo");
    }
}
