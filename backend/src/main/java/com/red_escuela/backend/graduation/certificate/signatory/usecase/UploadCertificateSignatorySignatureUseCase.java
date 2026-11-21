package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureUpload;

public interface UploadCertificateSignatorySignatureUseCase {

    CertificateSignatoryDetailResponse upload(Long id, SignatorySignatureUpload upload);
}
