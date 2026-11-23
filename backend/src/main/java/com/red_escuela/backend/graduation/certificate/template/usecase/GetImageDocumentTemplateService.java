package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetImageDocumentTemplateService implements GetImageDocumentTemplateUseCase {

    private final CertificateTemplateReader delegate;

    public GetImageDocumentTemplateService(CertificateTemplateReader delegate) {
        this.delegate = delegate;
    }

    @Override
    public CertificateTemplateImageContent get(DocumentTemplateType type, Long imageId) {
        return delegate.readImage(type, imageId);
    }
}
