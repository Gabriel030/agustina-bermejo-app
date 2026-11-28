package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectRegularException extends ConflictException {
    public SubjectRegularException(Long programEnrollmentId, Long subjectId) {
        super("La materia " + subjectId + " está regular en la matriculación " + programEnrollmentId
                + ": no se puede reconocer por equivalencia");
    }
}
