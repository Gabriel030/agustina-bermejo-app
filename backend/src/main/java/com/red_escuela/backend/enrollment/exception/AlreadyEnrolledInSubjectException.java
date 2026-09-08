package com.red_escuela.backend.enrollment.exception;

public class AlreadyEnrolledInSubjectException extends RuntimeException {
    public AlreadyEnrolledInSubjectException(Long programEnrollmentId, Long subjectId) {
        super("Ya existe una inscripción activa a otra comisión de la materia " + subjectId
                + " bajo la matriculación " + programEnrollmentId);
    }
}
