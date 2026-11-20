package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateNotRevocableException extends ConflictException {

    public GraduationCertificateNotRevocableException() {
        super("Solo se puede revocar o reemitir un certificado vigente");
    }
}
