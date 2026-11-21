package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateCertificateSignatoryService implements UpdateCertificateSignatoryUseCase {

    @Override
    public CertificateSignatoryDetailResponse update(Long id, SaveCertificateSignatoryRequest request) {
        throw new GraduationCertificateOperationPendingException();
    }
}
