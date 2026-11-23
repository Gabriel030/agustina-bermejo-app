package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidGraduationCertificateReviewDecisionException extends ConflictException {
    public InvalidGraduationCertificateReviewDecisionException(Long graduationRequestId) {
        super("No se puede decidir la revisión del certificado del trámite de egreso " + graduationRequestId + " en su estado actual");
    }
}
