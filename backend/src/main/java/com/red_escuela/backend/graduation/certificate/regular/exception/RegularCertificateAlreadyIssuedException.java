package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RegularCertificateAlreadyIssuedException extends ConflictException {

    public RegularCertificateAlreadyIssuedException() {
        super("Ya existe una constancia de alumno regular vigente para esta inscripción");
    }
}
