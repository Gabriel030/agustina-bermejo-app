package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RegularCertificateNotRevocableException extends ConflictException {

    public RegularCertificateNotRevocableException() {
        super("Solo se puede revocar una constancia vigente");
    }
}
