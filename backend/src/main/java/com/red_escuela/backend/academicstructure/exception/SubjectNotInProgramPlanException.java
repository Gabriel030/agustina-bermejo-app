package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectNotInProgramPlanException extends ConflictException {
    public SubjectNotInProgramPlanException(Long subjectId, Long programId) {
        super("La materia " + subjectId + " no está en el plan vigente de la carrera " + programId);
    }
}
