package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class GraduationCertificateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public GraduationCertificateNotFoundException(Long id) {
        super("No existe el certificado de egreso con id " + id);
    }
}
