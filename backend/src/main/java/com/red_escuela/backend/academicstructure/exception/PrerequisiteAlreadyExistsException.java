package com.red_escuela.backend.academicstructure.exception;

public class PrerequisiteAlreadyExistsException extends RuntimeException {
    public PrerequisiteAlreadyExistsException(Long programSubjectId, Long prerequisiteProgramSubjectId) {
        super("Ya existe esa correlatividad entre " + programSubjectId + " y " + prerequisiteProgramSubjectId);
    }
}
