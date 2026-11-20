package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class WithdrawCertificateExceptionService implements WithdrawCertificateExceptionUseCase {

    private final CertificateExceptionDecider specialist;

    public WithdrawCertificateExceptionService(CertificateExceptionDecider specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse withdraw(Long exceptionId) {
        return specialist.withdraw(exceptionId);
    }
}
