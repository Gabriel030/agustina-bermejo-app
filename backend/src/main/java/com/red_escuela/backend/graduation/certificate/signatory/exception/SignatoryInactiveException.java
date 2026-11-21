package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SignatoryInactiveException extends ConflictException {

    public SignatoryInactiveException() {
        super("El firmante está inactivo y no se puede elegir");
    }
}
