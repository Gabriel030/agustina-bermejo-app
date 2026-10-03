package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CorequisiteAlreadyExistsException extends ConflictException {
    public CorequisiteAlreadyExistsException(Long programSubjectId, Long corequisiteProgramSubjectId) {
        super("Ya existe ese co-requisito entre " + programSubjectId + " y " + corequisiteProgramSubjectId);
    }
}
