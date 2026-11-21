package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;

public interface GetCertificateSignatoryUseCase {

    CertificateSignatoryDetailResponse get(Long id);
}
