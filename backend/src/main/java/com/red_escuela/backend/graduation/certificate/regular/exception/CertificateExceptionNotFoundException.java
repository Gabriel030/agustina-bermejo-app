package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CertificateExceptionNotFoundException extends NotFoundException {

    public CertificateExceptionNotFoundException() {
        super("La excepción no existe");
    }
}
