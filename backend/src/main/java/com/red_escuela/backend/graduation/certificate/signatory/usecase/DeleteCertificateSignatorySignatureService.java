package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatorySignatureWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeleteCertificateSignatorySignatureService implements DeleteCertificateSignatorySignatureUseCase {

    private final CertificateSignatorySignatureWriter specialist;

    public DeleteCertificateSignatorySignatureService(CertificateSignatorySignatureWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse delete(Long id) {
        return specialist.delete(id);
    }
}
