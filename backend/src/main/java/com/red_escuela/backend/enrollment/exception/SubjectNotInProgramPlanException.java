package com.red_escuela.backend.enrollment.exception;

public class SubjectNotInProgramPlanException extends RuntimeException {
    public SubjectNotInProgramPlanException(Long subjectId, Long programId) {
        super("La materia " + subjectId + " no está en el plan de la carrera " + programId);
    }
}
