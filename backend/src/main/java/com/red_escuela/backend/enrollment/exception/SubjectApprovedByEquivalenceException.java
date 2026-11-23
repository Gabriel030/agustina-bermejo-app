package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectApprovedByEquivalenceException extends ConflictException {
    public SubjectApprovedByEquivalenceException(Long programEnrollmentId, Long subjectId) {
        super("La materia " + subjectId + " ya está aprobada por equivalencia bajo la matriculación "
                + programEnrollmentId + " y no se puede cursar");
    }
}
