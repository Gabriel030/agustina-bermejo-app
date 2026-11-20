package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RegularCertificateRequestNotPendingException extends ConflictException {

    public RegularCertificateRequestNotPendingException() {
        super("El pedido ya no está pendiente");
    }
}
