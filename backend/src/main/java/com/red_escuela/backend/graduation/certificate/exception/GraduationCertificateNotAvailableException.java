package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateNotAvailableException extends ConflictException {

    public GraduationCertificateNotAvailableException() {
        super("El certificado todavía no fue emitido");
    }
}
