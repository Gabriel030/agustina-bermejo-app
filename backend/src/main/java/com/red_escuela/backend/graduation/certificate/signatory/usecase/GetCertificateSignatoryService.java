package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateSignatoryService implements GetCertificateSignatoryUseCase {

    @Override
    public CertificateSignatoryDetailResponse get(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
