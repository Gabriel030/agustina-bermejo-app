package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateVersionService implements GetCertificateTemplateVersionUseCase {

    private final CertificateTemplateReader reader;

    public GetCertificateTemplateVersionService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public CertificateTemplateVersionResponse get(GraduationCertificateKind kind, int versionNumber) {
        return reader.readVersion(kind, versionNumber);
    }
}
