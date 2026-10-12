package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InstructorAlreadyAssignedException extends ConflictException {
    public InstructorAlreadyAssignedException(Long instructorId, Long sectionId) {
        super("El instructor " + instructorId + " ya está asignado con ese rol a la comisión " + sectionId);
    }
}
