package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CertificateRevokedException extends ConflictException {

    public CertificateRevokedException() {
        super("La constancia fue revocada");
    }
}
