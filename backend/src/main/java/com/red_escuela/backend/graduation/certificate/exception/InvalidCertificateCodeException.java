package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidCertificateCodeException extends BadRequestException {

    public InvalidCertificateCodeException() {
        super("El código del certificado no es válido");
    }
}
