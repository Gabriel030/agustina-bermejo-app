package com.red_escuela.backend.graduation.certificate.verification.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CertificateNotFoundException extends NotFoundException {

    public CertificateNotFoundException() {
        super("No se encontró un documento con ese código");
    }
}
