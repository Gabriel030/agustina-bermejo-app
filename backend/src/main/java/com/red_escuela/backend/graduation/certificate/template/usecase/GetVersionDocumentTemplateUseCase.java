package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface GetVersionDocumentTemplateUseCase {

    CertificateTemplateVersionResponse get(DocumentTemplateType type, int number);
}
