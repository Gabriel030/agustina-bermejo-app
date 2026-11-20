package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadCertificateTemplateImageService implements UploadCertificateTemplateImageUseCase {

    @Override
    public CertificateTemplateImageResponse upload(GraduationCertificateKind kind, CertificateTemplateImageUpload upload) {
        throw new GraduationCertificateOperationPendingException();
    }
}
