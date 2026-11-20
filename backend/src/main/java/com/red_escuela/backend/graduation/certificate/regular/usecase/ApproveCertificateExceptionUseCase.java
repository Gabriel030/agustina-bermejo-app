package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;

public interface ApproveCertificateExceptionUseCase {

    CertificateExceptionResponse approve(Long exceptionId, String decisionReason);
}
