package com.red_escuela.backend.graduation.certificate.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentTemplateResponse(DocumentTemplateType documentType, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) CertificateTemplateVersionResponse draft, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) CertificateTemplateVersionResponse published, boolean hasUnpublishedChanges) {
}
