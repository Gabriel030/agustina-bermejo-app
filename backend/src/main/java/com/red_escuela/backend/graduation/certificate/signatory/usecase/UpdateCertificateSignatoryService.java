package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateCertificateSignatoryService implements UpdateCertificateSignatoryUseCase {

    private final CertificateSignatoryWriter specialist;

    public UpdateCertificateSignatoryService(CertificateSignatoryWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse update(Long id, SaveCertificateSignatoryRequest request) {
        return specialist.update(id, request);
    }
}
