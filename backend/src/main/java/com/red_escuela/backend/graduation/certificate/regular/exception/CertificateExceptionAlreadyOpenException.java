package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CertificateExceptionAlreadyOpenException extends ConflictException {

    public CertificateExceptionAlreadyOpenException() {
        super("Ya hay una excepción pendiente o aprobada para esta inscripción");
    }
}
