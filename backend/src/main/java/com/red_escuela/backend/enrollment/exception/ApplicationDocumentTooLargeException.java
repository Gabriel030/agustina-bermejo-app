package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ApplicationDocumentTooLargeException extends BadRequestException {
    public ApplicationDocumentTooLargeException(String documentType) {
        super("El archivo " + documentType + " supera el máximo de 3 MB");
    }
}
