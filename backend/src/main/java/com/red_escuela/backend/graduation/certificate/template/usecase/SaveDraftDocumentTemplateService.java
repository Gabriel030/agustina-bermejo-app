package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateDraftSaver;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveDraftDocumentTemplateService implements SaveDraftDocumentTemplateUseCase {

    private final CertificateTemplateDraftSaver delegate;

    public SaveDraftDocumentTemplateService(CertificateTemplateDraftSaver delegate) {
        this.delegate = delegate;
    }

    @Override
    public CertificateTemplateVersionResponse save(DocumentTemplateType type, CertificateTemplateContent content, Integer basedOnVersionNumber) {
        return delegate.save(type, content, basedOnVersionNumber);
    }
}
