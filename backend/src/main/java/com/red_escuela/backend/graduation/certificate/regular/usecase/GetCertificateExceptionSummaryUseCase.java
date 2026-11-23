package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionSummaryResponse;

public interface GetCertificateExceptionSummaryUseCase {

    CertificateExceptionSummaryResponse summary(boolean seesAll);
}
