package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateImageService implements GetCertificateTemplateImageUseCase {

    private final CertificateTemplateReader reader;

    public GetCertificateTemplateImageService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public CertificateTemplateImageContent get(GraduationCertificateKind kind, Long imageId) {
        return reader.readImage(kind, imageId);
    }
}
