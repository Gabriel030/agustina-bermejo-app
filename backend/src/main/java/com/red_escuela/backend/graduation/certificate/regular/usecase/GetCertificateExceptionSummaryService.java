package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateExceptionSummaryService implements GetCertificateExceptionSummaryUseCase {

    private final CertificateExceptionReader specialist;

    public GetCertificateExceptionSummaryService(CertificateExceptionReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionSummaryResponse summary(boolean seesAll) {
        return specialist.summary(seesAll);
    }
}
