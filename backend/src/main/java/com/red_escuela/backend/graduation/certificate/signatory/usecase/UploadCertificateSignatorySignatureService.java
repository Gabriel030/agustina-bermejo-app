package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureUpload;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadCertificateSignatorySignatureService implements UploadCertificateSignatorySignatureUseCase {

    @Override
    public CertificateSignatoryDetailResponse upload(Long id, SignatorySignatureUpload upload) {
        throw new GraduationCertificateOperationPendingException();
    }
}
