package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;

public interface DeleteCertificateSignatorySignatureUseCase {

    CertificateSignatoryDetailResponse delete(Long id);
}
