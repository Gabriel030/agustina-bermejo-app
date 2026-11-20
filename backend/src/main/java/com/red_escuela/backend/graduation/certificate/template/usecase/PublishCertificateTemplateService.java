package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PublishCertificateTemplateService implements PublishCertificateTemplateUseCase {

    @Override
    public CertificateTemplateVersionResponse publish(GraduationCertificateKind kind) {
        throw new GraduationCertificateOperationPendingException();
    }
}
