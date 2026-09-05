package com.red_escuela.backend.exception;

public class CertificateNotEligibleException extends RuntimeException {
    public CertificateNotEligibleException(String reason) {
        super("El alumno no cumple la condición para emitir la constancia: " + reason);
    }
}
