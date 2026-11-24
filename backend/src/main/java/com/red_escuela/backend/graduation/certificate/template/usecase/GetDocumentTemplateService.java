package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDocumentTemplateService implements GetDocumentTemplateUseCase {

    private final CertificateTemplateReader delegate;

    public GetDocumentTemplateService(CertificateTemplateReader delegate) {
        this.delegate = delegate;
    }

    @Override
    public DocumentTemplateResponse get(DocumentTemplateType type) {
        return delegate.read(type);
    }
}
