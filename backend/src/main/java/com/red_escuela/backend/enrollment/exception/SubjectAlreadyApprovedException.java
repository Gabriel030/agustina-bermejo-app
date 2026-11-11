package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectAlreadyApprovedException extends ConflictException {
    public SubjectAlreadyApprovedException(Long programEnrollmentId, Long subjectId) {
        super("La materia " + subjectId + " ya está aprobada bajo la matriculación " + programEnrollmentId
                + " y no se puede volver a cursar");
    }
}
