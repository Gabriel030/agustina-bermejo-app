package com.red_escuela.backend.academicstructure.exception;

public class CorequisiteAlreadyExistsException extends RuntimeException {
    public CorequisiteAlreadyExistsException(Long programSubjectId, Long corequisiteProgramSubjectId) {
        super("Ya existe ese co-requisito entre " + programSubjectId + " y " + corequisiteProgramSubjectId);
    }
}
