package com.red_escuela.backend.graduation.certificate.template.dto;

import java.time.LocalDateTime;

public record CertificateTemplateVersionSummaryResponse(Integer versionNumber, LocalDateTime publishedAt, String publishedBy) {
}
