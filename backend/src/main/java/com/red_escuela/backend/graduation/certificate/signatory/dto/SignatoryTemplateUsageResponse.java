package com.red_escuela.backend.graduation.certificate.signatory.dto;

import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;

public record SignatoryTemplateUsageResponse(
        SignatoryDocumentType documentType,
        String templateName,
        Integer versionNumber,
        TemplateVersionStatus status) {
}
