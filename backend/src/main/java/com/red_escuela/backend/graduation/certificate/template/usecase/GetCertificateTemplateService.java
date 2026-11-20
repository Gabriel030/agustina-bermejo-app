package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateService implements GetCertificateTemplateUseCase {

    private final CertificateTemplateReader reader;

    public GetCertificateTemplateService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public CertificateTemplateResponse get(GraduationCertificateKind kind) {
        return reader.read(kind);
    }
}
