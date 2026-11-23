package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationCertificateReviewRequiredException extends ConflictException {

    private final String reviewStatus;

    public GraduationCertificateReviewRequiredException(Long graduationRequestId, String reviewStatus) {
        super("El certificado del trámite de egreso " + graduationRequestId + " no está habilitado: revisión " + reviewStatus);
        this.reviewStatus = reviewStatus;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }
}
