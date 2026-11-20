package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import java.util.List;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCertificateTemplateVersionsService implements ListCertificateTemplateVersionsUseCase {

    private final CertificateTemplateReader reader;

    public ListCertificateTemplateVersionsService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public List<CertificateTemplateVersionSummaryResponse> list(GraduationCertificateKind kind) {
        return reader.listVersions(kind);
    }
}
