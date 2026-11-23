package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CertificatePdfIntegrityMismatchException extends ConflictException {

    public CertificatePdfIntegrityMismatchException() {
        super("El documento regenerado no coincide con el hash registrado al emitirlo");
    }
}
