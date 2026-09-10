package com.red_escuela.backend.enrollment.exception;

// Distinta de PrerequisiteNotMetException: acá el problema no es que falte cumplir una
// correlatividad, es que la Subject de la comisión ni siquiera está en el plan de esta
// carrera (combinación inconsistente de programEnrollmentId + sectionId).
public class SubjectNotInProgramPlanException extends RuntimeException {
    public SubjectNotInProgramPlanException(Long subjectId, Long programId) {
        super("La materia " + subjectId + " no está en el plan de la carrera " + programId);
    }
}
