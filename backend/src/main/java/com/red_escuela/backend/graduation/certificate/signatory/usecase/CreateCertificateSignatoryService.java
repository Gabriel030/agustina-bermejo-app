package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateCertificateSignatoryService implements CreateCertificateSignatoryUseCase {

    @Override
    public CertificateSignatoryDetailResponse create(SaveCertificateSignatoryRequest request) {
        throw new GraduationCertificateOperationPendingException();
    }
}
