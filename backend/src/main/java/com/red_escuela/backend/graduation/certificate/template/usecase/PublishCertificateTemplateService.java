package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplatePublisher;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PublishCertificateTemplateService implements PublishCertificateTemplateUseCase {

    private final CertificateTemplatePublisher publisher;

    public PublishCertificateTemplateService(CertificateTemplatePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public CertificateTemplateVersionResponse publish(GraduationCertificateKind kind) {
        return publisher.publish(kind);
    }
}
