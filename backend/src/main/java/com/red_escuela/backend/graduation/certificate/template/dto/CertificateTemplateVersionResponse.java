package com.red_escuela.backend.graduation.certificate.template.dto;

import org.springframework.lang.Nullable;
import java.time.LocalDateTime;

public record CertificateTemplateVersionResponse(
        Long id,
        Integer versionNumber,
        TemplateVersionStatus status,
        @Nullable Integer basedOnVersionNumber,
        CertificateTemplateContent content,
        LocalDateTime updatedAt,
        @Nullable LocalDateTime publishedAt,
        @Nullable String publishedBy) {
}
