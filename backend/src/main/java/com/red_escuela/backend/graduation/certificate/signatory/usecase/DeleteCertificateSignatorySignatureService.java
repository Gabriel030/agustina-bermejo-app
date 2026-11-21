package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeleteCertificateSignatorySignatureService implements DeleteCertificateSignatorySignatureUseCase {

    @Override
    public CertificateSignatoryDetailResponse delete(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
