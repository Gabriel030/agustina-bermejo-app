package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureUpload;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatorySignatureWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadCertificateSignatorySignatureService implements UploadCertificateSignatorySignatureUseCase {

    private final CertificateSignatorySignatureWriter specialist;

    public UploadCertificateSignatorySignatureService(CertificateSignatorySignatureWriter specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateSignatoryDetailResponse upload(Long id, SignatorySignatureUpload upload) {
        return specialist.upload(id, upload);
    }
}
