package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListVersionsDocumentTemplateService implements ListVersionsDocumentTemplateUseCase {

    private final CertificateTemplateReader reader;

    public ListVersionsDocumentTemplateService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public List<CertificateTemplateVersionSummaryResponse> list(DocumentTemplateType type) {
        return reader.listVersions(type);
    }
}
