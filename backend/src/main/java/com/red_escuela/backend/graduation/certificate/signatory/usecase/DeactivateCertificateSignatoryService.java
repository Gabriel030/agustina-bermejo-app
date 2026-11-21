package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateCertificateSignatoryService implements DeactivateCertificateSignatoryUseCase {

    private final CertificateSignatoryWriter specialist;

    public DeactivateCertificateSignatoryService(CertificateSignatoryWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse deactivate(Long id) {
        return specialist.changeActive(id, false);
    }
}
