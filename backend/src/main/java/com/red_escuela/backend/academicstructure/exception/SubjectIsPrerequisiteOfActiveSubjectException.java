package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectIsPrerequisiteOfActiveSubjectException extends ConflictException {
    public SubjectIsPrerequisiteOfActiveSubjectException(Long programSubjectId, String dependentName) {
        super("La materia " + programSubjectId + " es correlativa de \"" + dependentName
                + "\", que sigue activa: quitá la correlatividad antes de desactivarla");
    }
}
