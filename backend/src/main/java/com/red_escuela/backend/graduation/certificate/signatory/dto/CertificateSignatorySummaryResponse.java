package com.red_escuela.backend.graduation.certificate.signatory.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CertificateSignatorySummaryResponse(
        Long id,
        String name,
        String role,
        boolean active,
        boolean hasSignature,
        List<SignatoryDocumentType> allowedDocumentTypes,
        List<SignatoryTemplateUsageResponse> usedInTemplates,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
