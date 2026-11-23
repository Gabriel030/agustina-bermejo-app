package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateDraftDiscarder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DiscardDraftDocumentTemplateService implements DiscardDraftDocumentTemplateUseCase {

    private final CertificateTemplateDraftDiscarder delegate;

    public DiscardDraftDocumentTemplateService(CertificateTemplateDraftDiscarder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void discard(DocumentTemplateType type) {
        delegate.discard(type);
    }
}
