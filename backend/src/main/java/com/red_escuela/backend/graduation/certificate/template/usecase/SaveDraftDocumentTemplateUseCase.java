package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface SaveDraftDocumentTemplateUseCase {

    CertificateTemplateVersionResponse save(DocumentTemplateType type, CertificateTemplateContent content, Integer basedOnVersionNumber);
}
