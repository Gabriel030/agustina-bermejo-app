package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyEnrolledInSubjectException extends ConflictException {
    public AlreadyEnrolledInSubjectException(Long programEnrollmentId, Long subjectId) {
        super("Ya existe una inscripción activa a otra comisión de la materia " + subjectId
                + " bajo la matriculación " + programEnrollmentId);
    }
}
