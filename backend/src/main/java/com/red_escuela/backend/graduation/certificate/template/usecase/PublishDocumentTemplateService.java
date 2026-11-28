package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplatePublisher;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PublishDocumentTemplateService implements PublishDocumentTemplateUseCase {

    private final CertificateTemplatePublisher publisher;

    public PublishDocumentTemplateService(CertificateTemplatePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public CertificateTemplateVersionResponse publish(DocumentTemplateType type) {
        return publisher.publish(type);
    }
}
