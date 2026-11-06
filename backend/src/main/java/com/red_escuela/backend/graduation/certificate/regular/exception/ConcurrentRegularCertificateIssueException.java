package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ConcurrentRegularCertificateIssueException extends ConflictException {

    public ConcurrentRegularCertificateIssueException() {
        super("Otra emisión de la constancia está en curso para esta inscripción, reintentá");
    }
}
