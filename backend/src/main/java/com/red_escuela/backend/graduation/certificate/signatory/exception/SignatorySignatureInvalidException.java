package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class SignatorySignatureInvalidException extends UnprocessableEntityException {

    public SignatorySignatureInvalidException() {
        super("La firma debe ser un PNG de hasta 2 MB");
    }
}
