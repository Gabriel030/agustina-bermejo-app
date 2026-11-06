package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateNotEligibleException extends ConflictException {

    public GraduationCertificateNotEligibleException(String reason) {
        super("No se puede emitir el certificado de egreso: " + reason);
    }
}
