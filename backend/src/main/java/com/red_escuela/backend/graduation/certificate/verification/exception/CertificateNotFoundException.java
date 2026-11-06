package com.red_escuela.backend.graduation.certificate.verification.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class CertificateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public CertificateNotFoundException() {
        super("No se encontró un documento con ese código");
    }
}
