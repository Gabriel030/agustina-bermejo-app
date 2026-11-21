package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SignatoryNotAllowedForDocumentTypeException extends ConflictException {

    public SignatoryNotAllowedForDocumentTypeException() {
        super("El firmante no tiene permiso para firmar este tipo de documento");
    }
}
