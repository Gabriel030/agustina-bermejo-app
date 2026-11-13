package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CertificateExpiredException extends ConflictException {

    public CertificateExpiredException() {
        super("La constancia está vencida: pedí una nueva");
    }
}
