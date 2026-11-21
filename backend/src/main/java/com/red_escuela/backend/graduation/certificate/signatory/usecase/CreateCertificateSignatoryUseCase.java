package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;

public interface CreateCertificateSignatoryUseCase {

    CertificateSignatoryDetailResponse create(SaveCertificateSignatoryRequest request);
}
