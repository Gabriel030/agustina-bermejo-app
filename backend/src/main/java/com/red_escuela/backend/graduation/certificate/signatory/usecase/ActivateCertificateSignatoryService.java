package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ActivateCertificateSignatoryService implements ActivateCertificateSignatoryUseCase {

    private final CertificateSignatoryWriter specialist;

    public ActivateCertificateSignatoryService(CertificateSignatoryWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse activate(Long id) {
        return specialist.changeActive(id, true);
    }
}
