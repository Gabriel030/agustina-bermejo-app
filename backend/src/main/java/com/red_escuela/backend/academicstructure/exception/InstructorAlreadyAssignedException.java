package com.red_escuela.backend.academicstructure.exception;

public class InstructorAlreadyAssignedException extends RuntimeException {
    public InstructorAlreadyAssignedException(Long instructorId, Long sectionId) {
        super("El instructor " + instructorId + " ya está asignado con ese rol a la comisión " + sectionId);
    }
}
