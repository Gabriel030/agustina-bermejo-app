package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class SubjectNotInProgramPlanException extends BadRequestException {
    public SubjectNotInProgramPlanException(Long subjectId, Long programId) {
        super("La materia " + subjectId + " no está en el plan de la carrera " + programId);
    }
}
