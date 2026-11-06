package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class GraduationCertificateNotFoundException extends NotFoundException {

    public GraduationCertificateNotFoundException(Long id) {
        super("No existe el certificado de egreso con id " + id);
    }
}
