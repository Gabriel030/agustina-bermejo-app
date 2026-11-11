package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ApplicationDocumentNotFoundException extends NotFoundException {
    public ApplicationDocumentNotFoundException(Long applicationId, Long documentId) {
        super("Documento " + documentId + " no encontrado en la solicitud " + applicationId);
    }
}
