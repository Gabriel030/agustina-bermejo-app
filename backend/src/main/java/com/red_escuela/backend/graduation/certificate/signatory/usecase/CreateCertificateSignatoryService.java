package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateCertificateSignatoryService implements CreateCertificateSignatoryUseCase {

    private final CertificateSignatoryWriter specialist;

    public CreateCertificateSignatoryService(CertificateSignatoryWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse create(SaveCertificateSignatoryRequest request) {
        return specialist.create(request);
    }
}
