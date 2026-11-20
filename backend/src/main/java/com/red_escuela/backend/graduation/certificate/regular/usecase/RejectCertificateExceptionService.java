package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectCertificateExceptionService implements RejectCertificateExceptionUseCase {

    private final CertificateExceptionDecider specialist;

    public RejectCertificateExceptionService(CertificateExceptionDecider specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse reject(Long exceptionId, String decisionReason) {
        return specialist.reject(exceptionId, decisionReason);
    }
}
