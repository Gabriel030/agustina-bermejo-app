package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetVersionDocumentTemplateService implements GetVersionDocumentTemplateUseCase {

    private final CertificateTemplateReader reader;

    public GetVersionDocumentTemplateService(CertificateTemplateReader reader) {
        this.reader = reader;
    }

    @Override
    public CertificateTemplateVersionResponse get(DocumentTemplateType type, int number) {
        return reader.readVersion(type, number);
    }
}
