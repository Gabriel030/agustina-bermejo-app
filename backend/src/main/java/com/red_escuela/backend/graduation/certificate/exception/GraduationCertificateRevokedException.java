package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateRevokedException extends ConflictException {

    public GraduationCertificateRevokedException() {
        super("El certificado fue revocado");
    }
}
