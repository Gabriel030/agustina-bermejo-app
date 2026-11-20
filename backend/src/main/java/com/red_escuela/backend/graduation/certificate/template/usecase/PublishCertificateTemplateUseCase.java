package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;

public interface PublishCertificateTemplateUseCase {

    CertificateTemplateVersionResponse publish(GraduationCertificateKind kind);
}
