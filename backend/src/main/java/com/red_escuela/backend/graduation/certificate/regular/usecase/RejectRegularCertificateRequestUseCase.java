package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;

public interface RejectRegularCertificateRequestUseCase {

    RegularCertificateRequestResponse reject(Long requestId, String reason);
}
