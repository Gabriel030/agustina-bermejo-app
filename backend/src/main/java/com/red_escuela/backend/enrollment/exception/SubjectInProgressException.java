package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectInProgressException extends ConflictException {
    public SubjectInProgressException(Long programEnrollmentId, Long subjectId) {
        super("La materia " + subjectId + " se está cursando en la matriculación " + programEnrollmentId + ": primero hay que dar de baja la cursada");
    }
}
