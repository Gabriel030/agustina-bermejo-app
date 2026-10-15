package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CertificateNotEligibleException extends BadRequestException {
    public CertificateNotEligibleException(String motivo) {
        super("El alumno no cumple la condición para emitir la constancia: " + motivo);
    }
}
