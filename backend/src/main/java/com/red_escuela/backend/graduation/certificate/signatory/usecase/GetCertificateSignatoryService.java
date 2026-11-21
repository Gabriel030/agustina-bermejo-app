package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateSignatoryService implements GetCertificateSignatoryUseCase {

    private final CertificateSignatoryReader specialist;

    public GetCertificateSignatoryService(CertificateSignatoryReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse get(Long id) {
        return specialist.get(id);
    }
}
