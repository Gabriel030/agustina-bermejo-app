package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveCertificateExceptionService implements ApproveCertificateExceptionUseCase {

    private final CertificateExceptionDecider specialist;

    public ApproveCertificateExceptionService(CertificateExceptionDecider specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse approve(Long exceptionId, String decisionReason) {
        return specialist.approve(exceptionId, decisionReason);
    }
}
