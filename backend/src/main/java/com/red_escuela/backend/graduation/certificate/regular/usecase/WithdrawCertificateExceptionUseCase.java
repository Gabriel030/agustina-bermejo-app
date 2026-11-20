package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;

public interface WithdrawCertificateExceptionUseCase {

    CertificateExceptionResponse withdraw(Long exceptionId);
}
