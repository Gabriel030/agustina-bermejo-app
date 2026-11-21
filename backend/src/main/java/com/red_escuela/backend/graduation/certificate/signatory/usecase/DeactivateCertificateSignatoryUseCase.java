package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;

public interface DeactivateCertificateSignatoryUseCase {

    CertificateSignatoryDetailResponse deactivate(Long id);
}
