package com.red_escuela.backend.graduation.certificate.template.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import io.swagger.v3.oas.annotations.media.Schema;

public record CertificateTemplateResponse(
        GraduationCertificateKind kind,
        @Schema(nullable = true) CertificateTemplateVersionResponse published,
        @Schema(nullable = true) CertificateTemplateVersionResponse draft,
        boolean hasUnpublishedChanges) {
}
