package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class GraduationCertificateOperationPendingException extends UnprocessableEntityException {

    public GraduationCertificateOperationPendingException() {
        super("Operación de certificados todavía no implementada");
    }
}
