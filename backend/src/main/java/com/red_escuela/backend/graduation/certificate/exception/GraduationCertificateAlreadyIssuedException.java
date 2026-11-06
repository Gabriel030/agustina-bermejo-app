package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateAlreadyIssuedException extends ConflictException {

    public GraduationCertificateAlreadyIssuedException() {
        super("Ya existe un certificado de egreso vigente para esta inscripción");
    }
}
