package com.red_escuela.backend.evaluation.exception;

public class NotSectionLeadInstructorException extends RuntimeException {
    public NotSectionLeadInstructorException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no es el Titular de la comisión " + sectionId);
    }
}
