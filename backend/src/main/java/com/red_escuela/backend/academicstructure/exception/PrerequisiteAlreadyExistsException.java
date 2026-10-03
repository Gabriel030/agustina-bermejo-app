package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PrerequisiteAlreadyExistsException extends ConflictException {
    public PrerequisiteAlreadyExistsException(Long programSubjectId, Long prerequisiteProgramSubjectId) {
        super("Ya existe esa correlatividad entre " + programSubjectId + " y " + prerequisiteProgramSubjectId);
    }
}
