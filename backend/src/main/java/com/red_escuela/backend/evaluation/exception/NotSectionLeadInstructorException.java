package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotSectionLeadInstructorException extends ForbiddenException {
    public NotSectionLeadInstructorException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no es el Titular de la comisión " + sectionId);
    }
}
