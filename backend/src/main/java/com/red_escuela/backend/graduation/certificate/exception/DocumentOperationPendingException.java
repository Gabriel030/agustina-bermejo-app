package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class DocumentOperationPendingException extends UnprocessableEntityException {

    public DocumentOperationPendingException() {
        super("Operación de documentos todavía no implementada");
    }
}
