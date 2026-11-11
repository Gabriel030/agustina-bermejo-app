package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectAlreadyRegularException extends ConflictException {
    public SubjectAlreadyRegularException(Long programEnrollmentId, Long subjectId) {
        super("La materia " + subjectId + " ya está regularizada bajo la matriculación " + programEnrollmentId
                + " y no se puede volver a cursar");
    }
}
