package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateReviewRequiredException extends ConflictException {
    public GraduationCertificateReviewRequiredException(Long graduationRequestId, String reviewStatus) {
        super("El certificado del trámite de egreso " + graduationRequestId + " no está habilitado: revisión " + reviewStatus);
    }
}
