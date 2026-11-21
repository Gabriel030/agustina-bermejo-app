package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;

public interface GetCertificateExceptionUseCase {

    CertificateExceptionResponse get(Long exceptionId, boolean seesAll);
}
