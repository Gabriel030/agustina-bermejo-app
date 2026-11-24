package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class EquivalenceDocumentInvalidException extends UnprocessableEntityException {
    public EquivalenceDocumentInvalidException(Long documentId, Long programEnrollmentId) {
        super("El documento " + documentId + " no pertenece al alumno de la matriculación " + programEnrollmentId);
    }
}
