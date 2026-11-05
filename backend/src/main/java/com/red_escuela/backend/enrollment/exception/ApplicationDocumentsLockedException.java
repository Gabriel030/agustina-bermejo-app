package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ApplicationDocumentsLockedException extends ConflictException {
    public ApplicationDocumentsLockedException(Long applicationId, String status) {
        super("La solicitud " + applicationId + " está en estado " + status + " y ya no admite cambios de documentos");
    }
}
