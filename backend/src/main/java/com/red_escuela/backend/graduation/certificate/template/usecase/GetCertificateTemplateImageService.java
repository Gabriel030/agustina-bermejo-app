package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateImageService implements GetCertificateTemplateImageUseCase {

    @Override
    public CertificateTemplateImageContent get(GraduationCertificateKind kind, Long imageId) {
        throw new GraduationCertificateOperationPendingException();
    }
}
