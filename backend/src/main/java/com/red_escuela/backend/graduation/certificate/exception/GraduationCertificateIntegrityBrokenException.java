package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.DataIntegrityBrokenException;

public class GraduationCertificateIntegrityBrokenException extends DataIntegrityBrokenException {

    public GraduationCertificateIntegrityBrokenException(Long id) {
        super("El PDF regenerado del certificado de egreso " + id + " no coincide con el hash guardado");
    }
}
