package com.red_escuela.backend.graduation.certificate.exception;

public class GraduationCertificateBlockedByDebtException extends GraduationCertificateNotEligibleException {

    public GraduationCertificateBlockedByDebtException() {
        super("el alumno tiene deuda pendiente");
    }
}
