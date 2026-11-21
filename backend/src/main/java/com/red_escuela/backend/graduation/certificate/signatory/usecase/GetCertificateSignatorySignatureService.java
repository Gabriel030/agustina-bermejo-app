package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureContent;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateSignatorySignatureService implements GetCertificateSignatorySignatureUseCase {

    private final CertificateSignatoryReader specialist;

    public GetCertificateSignatorySignatureService(CertificateSignatoryReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public SignatorySignatureContent get(Long id) {
        return specialist.signature(id);
    }
}
