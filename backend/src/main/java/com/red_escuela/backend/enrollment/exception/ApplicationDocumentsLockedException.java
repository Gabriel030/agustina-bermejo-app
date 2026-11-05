package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class ApplicationDocumentsLockedException extends ConflictException {
    public ApplicationDocumentsLockedException(Long applicationId, ProgramEnrollmentApplicationStatus status) {
        super("La solicitud " + applicationId + " está en estado " + status + " y ya no admite cambios de documentos");
    }
}
