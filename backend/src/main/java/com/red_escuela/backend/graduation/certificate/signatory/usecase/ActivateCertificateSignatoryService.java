package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ActivateCertificateSignatoryService implements ActivateCertificateSignatoryUseCase {

    @Override
    public CertificateSignatoryDetailResponse activate(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
