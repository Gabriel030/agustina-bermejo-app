package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class RegularCertificateNotFoundException extends NotFoundException {

    public RegularCertificateNotFoundException() {
        super("La constancia de alumno regular no existe");
    }
}
