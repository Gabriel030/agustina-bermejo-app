package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidDocumentNumberException extends BadRequestException {

    public InvalidDocumentNumberException(DocumentType type) {
        super("Document number format is invalid for type " + type);
    }
}
