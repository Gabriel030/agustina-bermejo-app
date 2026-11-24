package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EquivalenceAlreadyActiveException extends ConflictException {
    public EquivalenceAlreadyActiveException(Long programEnrollmentId, Long subjectId) {
        super("Ya existe una equivalencia vigente para la materia " + subjectId + " en la matriculación " + programEnrollmentId);
    }
}
