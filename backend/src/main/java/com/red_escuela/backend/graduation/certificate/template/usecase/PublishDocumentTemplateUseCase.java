package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface PublishDocumentTemplateUseCase {

    CertificateTemplateVersionResponse publish(DocumentTemplateType type);
}
