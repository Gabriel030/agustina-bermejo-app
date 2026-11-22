package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface GetImageDocumentTemplateUseCase {

    CertificateTemplateImageContent get(DocumentTemplateType type, Long imageId);
}
