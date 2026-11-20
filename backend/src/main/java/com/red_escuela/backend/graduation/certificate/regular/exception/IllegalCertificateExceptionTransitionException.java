package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionEvent;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalCertificateExceptionTransitionException extends ConflictException {

    public IllegalCertificateExceptionTransitionException(CertificateExceptionStatus from, CertificateExceptionEvent event) {
        super("La excepción en estado " + from + " no admite " + event);
    }
}
