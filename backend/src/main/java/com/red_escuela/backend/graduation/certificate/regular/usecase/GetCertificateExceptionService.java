package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateExceptionService implements GetCertificateExceptionUseCase {

    private final CertificateExceptionReader specialist;

    public GetCertificateExceptionService(CertificateExceptionReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse get(Long exceptionId, boolean seesAll) {
        return specialist.get(exceptionId, seesAll);
    }
}
