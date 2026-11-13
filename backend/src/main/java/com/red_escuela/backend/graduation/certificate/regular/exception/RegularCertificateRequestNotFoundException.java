package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class RegularCertificateRequestNotFoundException extends NotFoundException {

    public RegularCertificateRequestNotFoundException() {
        super("El pedido de constancia no existe");
    }
}
