package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;

public interface RejectCertificateExceptionUseCase {

    CertificateExceptionResponse reject(Long exceptionId, String decisionReason);
}
